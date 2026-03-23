package com.haier.emptyscreen.service;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.haier.emptyscreen.EmptyScreenApplication;
import com.haier.emptyscreen.LauncherActivity;
import com.haier.emptyscreen.MainActivity;
import com.haier.emptyscreen.R;
import com.haier.emptyscreen.SettingsActivity;
import com.haier.emptyscreen.VideoPlayerActivity;
import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.MemoryUtils;
import com.haier.emptyscreen.utils.PrefsManager;

/**
 * 前台服务 - 保持应用运行并监控内存
 */
public class ForegroundService extends Service {

    private static final String CHANNEL_ID = "empty_screen_service";
    private static final int NOTIFICATION_ID = 1001;

    private Handler mHandler;
    private Runnable mMemoryCheckRunnable;
    private Runnable mBringToFrontRunnable;
    private PrefsManager mPrefsManager;
    private Application.ActivityLifecycleCallbacks mLifecycleCallbacks;

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
        
        if (mPrefsManager.isMemoryCleanEnabled()) {
            setupMemoryCheck();
        }
        
        LogUtils.i("[ForegroundService] Started (event-driven mode)");
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
