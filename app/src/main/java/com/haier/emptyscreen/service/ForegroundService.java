package com.haier.emptyscreen.service;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import com.haier.emptyscreen.EmptyScreenApplication;
import com.haier.emptyscreen.LauncherActivity;
import com.haier.emptyscreen.MainActivity;
import com.haier.emptyscreen.R;
import com.haier.emptyscreen.SettingsActivity;
import com.haier.emptyscreen.VideoPlayerActivity;
import com.haier.emptyscreen.receiver.BootLaunchScheduler;
import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.MemoryUtils;
import com.haier.emptyscreen.utils.PrefsManager;

/**
 * 前台服务 - 保持应用运行并监控内存
 */
public class ForegroundService extends Service {

    private static final String CHANNEL_ID = "empty_screen_service";
    private static final int NOTIFICATION_ID = 1001;

    /** SCREEN_ON 节流间隔（毫秒）- 该时间内的重复亮屏只处理一次，避免频繁抢占 */
    private static final long SCREEN_ON_THROTTLE_MS = 30_000L;

    /** 判定为 STR 真睡眠的最小挂起时长（毫秒）- 两时钟差值超过此值才认为 CPU 真的挂起过 */
    private static final long STR_SUSPEND_THRESHOLD_MS = 3_000L;

    private Handler mHandler;
    private Runnable mMemoryCheckRunnable;
    private Runnable mBringToFrontRunnable;
    private PrefsManager mPrefsManager;
    private Application.ActivityLifecycleCallbacks mLifecycleCallbacks;

    /** 屏幕开关广播接收器（动态注册，用于待机唤醒后重新拉起应用） */
    private BroadcastReceiver mScreenReceiver;

    /** 上次因 SCREEN_ON 触发调度的时刻（用于节流） */
    private long mLastScreenOnHandledMs = 0L;

    /** 熄屏时刻的真实时间（elapsedRealtime，含挂起），-1 表示未记录 */
    private long mScreenOffElapsedMs = -1L;

    /** 熄屏时刻的 CPU 运行时间（uptimeMillis，挂起时冻结），-1 表示未记录 */
    private long mScreenOffUptimeMs = -1L;

    private int mResumedTargetActivityCount = 0;
    private boolean mIsAppInForeground = true;
    private boolean mIsBringToFrontScheduled = false;

    /**
     * 判断是否为目标 Activity
     */
    private boolean isTargetActivity(Activity activity) {
        return activity instanceof MainActivity ||
                activity instanceof SettingsActivity ||
                activity instanceof VideoPlayerActivity ||
                activity instanceof LauncherActivity;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mPrefsManager = PrefsManager.getInstance(this);
        mHandler = new Handler(Looper.getMainLooper());

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        setupLifecycleCallbacks();

        registerScreenReceiver();

        if (mPrefsManager.isMemoryCleanEnabled()) {
            setupMemoryCheck();
        }

        LogUtils.i("[ForegroundService] Started (event-driven mode)");
    }

    /**
     * 动态注册屏幕开关广播接收器
     *
     * <p>{@code SCREEN_ON}/{@code SCREEN_OFF} 系统不允许静态注册，必须在运行时动态注册。
     * 用于覆盖"待机/休眠唤醒（str）"场景：这种场景不会再发 BOOT_COMPLETED，
     * 只能靠 SCREEN_ON 感知唤醒，复用 {@link BootLaunchScheduler} 的自适应重试拉起。</p>
     */
    private void registerScreenReceiver() {
        if (mScreenReceiver != null) {
            return;
        }
        mScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) {
                    return;
                }
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    handleScreenOn(context);
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    handleScreenOff();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenReceiver, filter);
        LogUtils.i("[ForegroundService] Screen receiver registered");
    }

    /**
     * 处理屏幕点亮事件，多层过滤避免频繁抢占：
     *
     * <ol>
     *   <li>应用已在前台：说明只是正常使用中的亮屏，直接忽略</li>
     *   <li>区分纯亮屏 vs STR 待机唤醒：比对 elapsedRealtime 与 uptimeMillis 的增量差，
     *       差值大说明 CPU 真挂起过（STR 唤醒），接近 0 说明只是关背光（纯亮屏）→ 忽略</li>
     *   <li>节流：{@link #SCREEN_ON_THROTTLE_MS} 内的重复唤醒只处理一次</li>
     * </ol>
     *
     * <p>仅当"应用不在前台 且 判定为 STR 唤醒 且 超过节流间隔"时，才触发自适应重试拉起。</p>
     */
    private void handleScreenOn(Context context) {
        if (mIsAppInForeground) {
            LogUtils.i("[ForegroundService] SCREEN_ON ignored (app already foreground)");
            return;
        }

        // 区分纯亮屏与 STR 待机唤醒
        if (!isWokenFromSuspend()) {
            LogUtils.i("[ForegroundService] SCREEN_ON ignored (screen-on only, no CPU suspend)");
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - mLastScreenOnHandledMs < SCREEN_ON_THROTTLE_MS) {
            LogUtils.i("[ForegroundService] SCREEN_ON throttled ("
                    + (now - mLastScreenOnHandledMs) + "ms since last)");
            return;
        }
        mLastScreenOnHandledMs = now;

        LogUtils.i("[ForegroundService] SCREEN_ON accepted (STR wake) -> schedule launch");
        BootLaunchScheduler.onBootDetected(context);
    }

    /**
     * 记录熄屏时刻的双时钟基准，供下次亮屏判定是否经历过 CPU 挂起
     */
    private void handleScreenOff() {
        mScreenOffElapsedMs = SystemClock.elapsedRealtime();
        mScreenOffUptimeMs = SystemClock.uptimeMillis();
        LogUtils.i("[ForegroundService] SCREEN_OFF received (standby), baseline recorded");
    }

    /**
     * 判断本次亮屏是否由 STR 待机唤醒（CPU 真正挂起过）
     *
     * <p>原理：{@code elapsedRealtime} 含挂起时间，{@code uptimeMillis} 在挂起时冻结。
     * 熄屏到亮屏期间，两者增量之差 ≈ CPU 被挂起的时长。</p>
     *
     * <p>若未记录到熄屏基准（如服务在熄屏后才启动），保守返回 true，宁可多拉一次也不漏。</p>
     *
     * @return true-STR 唤醒或无法判定；false-仅亮屏未挂起
     */
    private boolean isWokenFromSuspend() {
        if (mScreenOffElapsedMs < 0 || mScreenOffUptimeMs < 0) {
            LogUtils.i("[ForegroundService] No screen-off baseline, assume STR wake");
            return true;
        }
        long elapsedDelta = SystemClock.elapsedRealtime() - mScreenOffElapsedMs;
        long uptimeDelta = SystemClock.uptimeMillis() - mScreenOffUptimeMs;
        long suspendedMs = elapsedDelta - uptimeDelta;

        // 用完即重置基准，避免陈旧数据影响后续判断
        mScreenOffElapsedMs = -1L;
        mScreenOffUptimeMs = -1L;

        LogUtils.i("[ForegroundService] suspendedMs=" + suspendedMs
                + " (elapsedDelta=" + elapsedDelta + ", uptimeDelta=" + uptimeDelta + ")");
        return suspendedMs >= STR_SUSPEND_THRESHOLD_MS;
    }

    /**
     * 设置 Activity 生命周期回调
     */
    private void setupLifecycleCallbacks() {
        mLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {
                if (isTargetActivity(activity)) {
                    mResumedTargetActivityCount++;
                    
                    if (!mIsAppInForeground) {
                        mIsAppInForeground = true;
                        cancelBringToFrontTask();
                        LogUtils.i("[ForegroundService] " + activity.getClass().getSimpleName() + 
                                " resumed -> foreground (count=" + mResumedTargetActivityCount + ")");
                    }
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (isTargetActivity(activity)) {
                    mResumedTargetActivityCount--;
                    
                    if (mResumedTargetActivityCount <= 0) {
                        mResumedTargetActivityCount = 0;
                        mIsAppInForeground = false;
                        scheduleBringToFrontTask();
                        LogUtils.i("[ForegroundService] " + activity.getClass().getSimpleName() + 
                                " paused -> background, will bring to front in " + 
                                mPrefsManager.getForegroundDelaySeconds() + "s");
                    }
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        };

        getApplication().registerActivityLifecycleCallbacks(mLifecycleCallbacks);
    }

    /**
     * 调度将应用带回前台的任务
     */
    private void scheduleBringToFrontTask() {
        if (mIsBringToFrontScheduled) {
            return;
        }

        int delaySeconds = mPrefsManager.getForegroundDelaySeconds();

        mBringToFrontRunnable = () -> {
            if (!mIsAppInForeground) {
                bringAppToFront();
                mIsAppInForeground = true;
                LogUtils.i("[ForegroundService] Brought app to front after delay");
            }
            mIsBringToFrontScheduled = false;
        };

        mHandler.postDelayed(mBringToFrontRunnable, delaySeconds * 1000L);
        mIsBringToFrontScheduled = true;
    }

    /**
     * 取消带回前台的任务
     */
    private void cancelBringToFrontTask() {
        if (mIsBringToFrontScheduled && mBringToFrontRunnable != null) {
            mHandler.removeCallbacks(mBringToFrontRunnable);
            mIsBringToFrontScheduled = false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mHandler != null) {
            if (mBringToFrontRunnable != null) {
                mHandler.removeCallbacks(mBringToFrontRunnable);
            }
            if (mMemoryCheckRunnable != null) {
                mHandler.removeCallbacks(mMemoryCheckRunnable);
            }
        }
        if (mLifecycleCallbacks != null) {
            getApplication().unregisterActivityLifecycleCallbacks(mLifecycleCallbacks);
        }
        if (mScreenReceiver != null) {
            try {
                unregisterReceiver(mScreenReceiver);
            } catch (Exception e) {
                LogUtils.w("[ForegroundService] unregister screen receiver failed: " + e.getMessage());
            }
            mScreenReceiver = null;
        }
        LogUtils.i("[ForegroundService] Destroyed");
        super.onDestroy();
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("EmptyScreen 运行服务");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建通知
     */
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, LauncherActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("服务运行中")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * 设置内存检查
     */
    private void setupMemoryCheck() {
        mMemoryCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkMemory();
                int interval = mPrefsManager.getMemoryCleanInterval();
                mHandler.postDelayed(this, interval * 1000L);
            }
        };
        mHandler.post(mMemoryCheckRunnable);
    }

    /**
     * 检查内存使用情况
     */
    private void checkMemory() {
        if (!mPrefsManager.isMemoryCleanEnabled()) {
            return;
        }

        int threshold = mPrefsManager.getMemoryCleanThreshold();
        float currentUsage = MemoryUtils.getSystemMemoryUsagePercent(this);

        if (currentUsage >= threshold) {
            LogUtils.w("[ForegroundService] Memory " + String.format("%.1f%%", currentUsage) + 
                    " >= " + threshold + "%, restarting app");
            EmptyScreenApplication.getInstance().restartApp();
        }
    }

    /**
     * 将应用带回前台
     */
    private void bringAppToFront() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bringAppToFrontWithNotification();
            } else {
                bringAppToFrontDirectly();
            }
        } catch (Exception e) {
            LogUtils.e("[ForegroundService] Bring to front failed: " + e.getMessage());
        }
    }

    /**
     * 直接启动应用
     */
    private void bringAppToFrontDirectly() {
        Intent intent = new Intent(this, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /**
     * 通过通知将应用带回前台（Android 10+）
     */
    private void bringAppToFrontWithNotification() {
        Intent intent = new Intent(this, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                NOTIFICATION_ID + 1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("点击返回应用")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
        }

        try {
            startActivity(intent);
        } catch (Exception e) {
            LogUtils.w("[ForegroundService] Direct start failed: " + e.getMessage());
        }
    }
}
