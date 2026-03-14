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
import com.haier.emptyscreen.utils.PrefsManager;

public class ForegroundService extends Service {

    private static final String CHANNEL_ID = "empty_screen_service";
    private static final int NOTIFICATION_ID = 1001;
    private static final long CHECK_INTERVAL = 1000;

    private Handler mHandler;
    private Runnable mForegroundCheckRunnable;
    private PrefsManager mPrefsManager;
    private boolean mIsChecking = false;
    private long mLastForegroundTime = 0;
    private int mCheckCount = 0;
    private Application.ActivityLifecycleCallbacks mLifecycleCallbacks;
    private int mResumedTargetActivityCount = 0;
    private boolean mIsAppInForeground = true;

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
        if (mHandler != null && mForegroundCheckRunnable != null) {
            mHandler.removeCallbacks(mForegroundCheckRunnable);
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
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            LogUtils.i("[ForegroundService] App brought to front successfully");
        } catch (Exception e) {
            LogUtils.e("[ForegroundService] Failed to bring app to front: " + e.getMessage());
        }
    }
}
