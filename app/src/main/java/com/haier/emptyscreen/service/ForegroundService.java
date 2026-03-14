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

import com.haier.emptyscreen.MainActivity;
import com.haier.emptyscreen.R;
import com.haier.emptyscreen.SettingsActivity;
import com.haier.emptyscreen.VideoPlayerActivity;
import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.MemoryCleaner;
import com.haier.emptyscreen.utils.MemoryUtils;
import com.haier.emptyscreen.utils.PrefsManager;

/**
 * 前台服务 - 负责应用保活、后台拉起和内存监控
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>前台服务保活：通过通知栏常驻通知保持服务运行</li>
 *   <li>生命周期监控：使用ActivityLifecycleCallbacks监控目标Activity状态</li>
 *   <li>后台拉起：当所有目标Activity进入后台超过阈值时间后，自动拉起MainActivity</li>
 *   <li>内存监控：定时检查系统内存使用率，超阈值时自动清理</li>
 * </ul>
 * 
 * <p>目标Activity：MainActivity, SettingsActivity, VideoPlayerActivity</p>
 * 
 * <p>工作原理：</p>
 * <ol>
 *   <li>使用计数器mResumedTargetActivityCount跟踪目标Activity的resumed状态</li>
 *   <li>当计数器归零时，标记应用进入后台，记录时间戳</li>
 *   <li>定时检查后台时间，超过配置阈值后启动MainActivity</li>
 *   <li>定时检查内存使用率，超过阈值时触发MemoryCleaner清理</li>
 * </ol>
 * 
 * @author EmptyScreen Team
 * @version 1.0
 */
public class ForegroundService extends Service {

    /** 通知渠道ID */
    private static final String CHANNEL_ID = "empty_screen_service";
    
    /** 通知ID */
    private static final int NOTIFICATION_ID = 1001;
    
    /** 前台检查间隔（毫秒） */
    private static final long CHECK_INTERVAL = 1000;
    
    /** 内存检查默认间隔（毫秒） */
    private static final long MEMORY_CHECK_INTERVAL = 60000;

    /** 主线程Handler */
    private Handler mHandler;
    
    /** 前台检查Runnable */
    private Runnable mForegroundCheckRunnable;
    
    /** 内存检查Runnable */
    private Runnable mMemoryCheckRunnable;
    
    /** 配置管理器 */
    private PrefsManager mPrefsManager;
    
    /** 是否正在检查（防止并发） */
    private boolean mIsChecking = false;
    
    /** 最后一次前台时间戳 */
    private long mLastForegroundTime = 0;
    
    /** 检查计数器（用于日志间隔） */
    private int mCheckCount = 0;
    
    /** Activity生命周期回调 */
    private Application.ActivityLifecycleCallbacks mLifecycleCallbacks;
    
    /** 处于resumed状态的目标Activity数量 */
    private int mResumedTargetActivityCount = 0;
    
    /** 应用是否在前台 */
    private boolean mIsAppInForeground = true;

    /**
     * 判断Activity是否为目标Activity
     * 
     * <p>只有MainActivity、SettingsActivity、VideoPlayerActivity被视为目标Activity，
     * 其他Activity（如对话框Activity）不影响前台状态判断</p>
     * 
     * @param activity 待判断的Activity
     * @return 是否为目标Activity
     */
    private boolean isTargetActivity(Activity activity) {
        return activity instanceof MainActivity || 
               activity instanceof SettingsActivity || 
               activity instanceof VideoPlayerActivity;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtils.i("[ForegroundService] onCreate");

        mPrefsManager = PrefsManager.getInstance(this);
        mHandler = new Handler(Looper.getMainLooper());

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        setupLifecycleCallbacks();
        setupForegroundCheck();
        setupMemoryCheck();
    }

    
    private void setupLifecycleCallbacks() {
        mLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (isTargetActivity(activity)) {
                    mResumedTargetActivityCount++;
                    LogUtils.i("[ForegroundService] " + activity.getClass().getSimpleName() + 
                            " resumed (targetCount=" + mResumedTargetActivityCount + ") - app is in foreground");
                    mIsAppInForeground = true;
                    mLastForegroundTime = System.currentTimeMillis();
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                LogUtils.i("[ForegroundService] " + activity.getClass().getSimpleName() + " paused");
                 if (isTargetActivity(activity)) {
                    mResumedTargetActivityCount--;
                    LogUtils.i("[ForegroundService] " + activity.getClass().getSimpleName() + 
                            " paused (targetCount=" + mResumedTargetActivityCount + ")");
                    if (mResumedTargetActivityCount <= 0) {
                        mResumedTargetActivityCount = 0;
                        mIsAppInForeground = false;
                        if (mLastForegroundTime == 0) {
                            mLastForegroundTime = System.currentTimeMillis();
                        }
                        LogUtils.i("[ForegroundService] All target activities paused, app is in background");
                    }
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                 LogUtils.i("[ForegroundService] " + activity.getClass().getSimpleName() + " stopped");
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        };
        
        getApplication().registerActivityLifecycleCallbacks(mLifecycleCallbacks);
        LogUtils.i("[ForegroundService] Lifecycle callbacks registered for MainActivity, SettingsActivity, VideoPlayerActivity");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.i("[ForegroundService] onStartCommand");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        LogUtils.i("[ForegroundService] onDestroy");
        if (mHandler != null) {
            if (mForegroundCheckRunnable != null) {
                mHandler.removeCallbacks(mForegroundCheckRunnable);
            }
            if (mMemoryCheckRunnable != null) {
                mHandler.removeCallbacks(mMemoryCheckRunnable);
            }
        }
        if (mLifecycleCallbacks != null) {
            getApplication().unregisterActivityLifecycleCallbacks(mLifecycleCallbacks);
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("EmptyScreen运行服务");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
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

    private void setupForegroundCheck() {
        mForegroundCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkAndBringToFront();
                mHandler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        mHandler.post(mForegroundCheckRunnable);
        LogUtils.i("[ForegroundService] Foreground check started");
    }

    private void setupMemoryCheck() {
        mMemoryCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkAndCleanMemory();
                int interval = mPrefsManager.getMemoryCleanInterval();
                mHandler.postDelayed(this, interval * 1000L);
            }
        };
        
        if (mPrefsManager.isMemoryCleanEnabled()) {
            mHandler.post(mMemoryCheckRunnable);
            LogUtils.i("[ForegroundService] Memory check started");
        }
    }

    private void checkAndCleanMemory() {
        if (!mPrefsManager.isMemoryCleanEnabled()) {
            return;
        }
        
        int threshold = mPrefsManager.getMemoryCleanThreshold();
        float currentUsage = MemoryUtils.getSystemMemoryUsagePercent(this);
        
        LogUtils.d("[ForegroundService] Memory check: current=" + String.format("%.1f%%", currentUsage) + 
                ", threshold=" + threshold + "%");
        
        if (currentUsage >= threshold) {
            LogUtils.i("[ForegroundService] Memory usage " + String.format("%.1f%%", currentUsage) + 
                    " >= threshold " + threshold + "%, triggering cleanup");
            
            new Thread(() -> {
                MemoryCleaner.CleanResult result = MemoryCleaner.cleanMemory(this);
                if (result.success) {
                    LogUtils.i("[ForegroundService] Auto memory clean completed. Freed: " + 
                            MemoryUtils.formatSize(result.freedBytes) + 
                            ", Before: " + String.format("%.1f%%", result.beforePercent) + 
                            ", After: " + String.format("%.1f%%", result.afterPercent));
                }
            }).start();
        }
    }

    private void checkAndBringToFront() {
        if (mIsChecking) {
            return;
        }

        mIsChecking = true;
        try {
            mCheckCount++;
            
            int delaySeconds = mPrefsManager.getForegroundDelaySeconds();
            long currentTime = System.currentTimeMillis();
            
            if (mCheckCount % 5 == 0) {
                LogUtils.d("[ForegroundService] Check #" + mCheckCount + 
                        " | isAppInForeground=" + mIsAppInForeground + 
                        " | targetActivityCount=" + mResumedTargetActivityCount +
                        " | delaySetting=" + delaySeconds + "s" +
                        " | lastForegroundTime=" + (mLastForegroundTime > 0 ? 
                                ((currentTime - mLastForegroundTime) / 1000) + "s ago" : "N/A"));
            }

            if (mIsAppInForeground) {
                mLastForegroundTime = currentTime;
            } else {
                if (mLastForegroundTime > 0) {
                    long elapsedSeconds = (currentTime - mLastForegroundTime) / 1000;
                    
                    if (elapsedSeconds >= delaySeconds) {
                        LogUtils.i("[ForegroundService] All target activities in background for " + elapsedSeconds + 
                                " seconds (threshold: " + delaySeconds + "s), bringing MainActivity to front");
                        bringAppToFront();
                        mLastForegroundTime = currentTime;
                        mIsAppInForeground = true;
                    } else {
                        if (mCheckCount % 5 == 0) {
                            LogUtils.d("[ForegroundService] Waiting for " + (delaySeconds - elapsedSeconds) + 
                                    " more seconds before bringing to front");
                        }
                    }
                }
            }
        } finally {
            mIsChecking = false;
        }
    }
    private void bringAppToFront() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bringAppToFrontWithNotification();
            } else {
                bringAppToFrontDirectly();
            }
        } catch (Exception e) {
            LogUtils.e("[ForegroundService] Failed to bring app to front: " + e.getMessage());
        }
    }
    
    private void bringAppToFrontDirectly() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        LogUtils.i("[ForegroundService] App brought to front directly");
    }
    
    private void bringAppToFrontWithNotification() {
        Intent intent = new Intent(this, MainActivity.class);
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
            LogUtils.i("[ForegroundService] Full-screen notification posted for Android 10+");
        }
        
        try {
            startActivity(intent);
            LogUtils.i("[ForegroundService] Direct start activity attempted");
        } catch (Exception e) {
            LogUtils.w("[ForegroundService] Direct start failed, notification shown: " + e.getMessage());
        }
    }
}
