package com.haier.emptyscreen.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import com.haier.emptyscreen.LauncherActivity;
import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.PrefsManager;

/**
 * 开机启动接收器 - 负责设备开机后延迟启动应用
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String ACTION_LAUNCH_APP = "com.haier.emptyscreen.ACTION_LAUNCH_APP";
    
    private static final int LAUNCH_REQUEST_CODE = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            LogUtils.w("[BootReceiver] Received null intent or action");
            return;
        }

        String action = intent.getAction();
        LogUtils.i("[BootReceiver] Received broadcast: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
            "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            scheduleAppLaunch(context);
        } else if (ACTION_LAUNCH_APP.equals(action)) {
            launchApp(context);
        }
    }

    /**
     * 调度应用启动（使用 AlarmManager 延迟启动）
     */
    private void scheduleAppLaunch(Context context) {
        LogUtils.i("[BootReceiver] Scheduling app launch");

        PrefsManager prefsManager = PrefsManager.getInstance(context);
        int delaySeconds = prefsManager.getBootDelaySeconds();

        LogUtils.i("[BootReceiver] Scheduling app launch after " + delaySeconds + " seconds");

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            LogUtils.e("[BootReceiver] AlarmManager is null, launching directly");
            launchApp(context);
            return;
        }

        Intent launchIntent = new Intent(context, BootReceiver.class);
        launchIntent.setAction(ACTION_LAUNCH_APP);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                LAUNCH_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerAtMillis = SystemClock.elapsedRealtime() + (delaySeconds * 1000L);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                );
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                );
            } else {
                alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                );
            }
            LogUtils.i("[BootReceiver] Alarm scheduled successfully for " + delaySeconds + " seconds later");
        } catch (Exception e) {
            LogUtils.e("[BootReceiver] Failed to schedule alarm: " + e.getMessage());
            launchApp(context);
        }
    }

    /**
     * 启动应用
     */
    private void launchApp(Context context) {
        LogUtils.i("[BootReceiver] Launching app");
        
        try {
            Intent launchIntent = new Intent(context, LauncherActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launchIntent);
            LogUtils.i("[BootReceiver] App launched successfully");
        } catch (Exception e) {
            LogUtils.e("[BootReceiver] Failed to launch app: " + e.getMessage());
        }
    }
}
