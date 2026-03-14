package com.haier.emptyscreen.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import com.haier.emptyscreen.MainActivity;
import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.PrefsManager;

/**
 * 开机启动接收器 - 负责设备开机后延迟启动应用
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>接收开机完成广播</li>
 *   <li>使用AlarmManager延迟启动应用（解决进程被杀死问题）</li>
 *   <li>支持多种开机广播（兼容不同厂商）</li>
 * </ul>
 * 
 * <p>支持的广播：</p>
 * <ul>
 *   <li>android.intent.action.BOOT_COMPLETED - 标准开机广播</li>
 *   <li>android.intent.action.QUICKBOOT_POWERON - 快速启动广播</li>
 *   <li>com.htc.intent.action.QUICKBOOT_POWERON - HTC设备快速启动</li>
 * </ul>
 * 
 * <p>工作原理：</p>
 * <ol>
 *   <li>接收到开机广播后，使用AlarmManager设置延迟闹钟</li>
 *   <li>闹钟触发时发送自定义广播ACTION_LAUNCH_APP</li>
 *   <li>接收到自定义广播后启动MainActivity</li>
 * </ol>
 * 
 * <p>为什么使用AlarmManager而不是Handler.postDelayed？</p>
 * <p>因为BroadcastReceiver.onReceive()执行完毕后，如果进程没有其他组件运行，
 * 系统会立即杀死进程，Handler的延迟任务会随进程死亡而被清除。
 * 使用AlarmManager可以确保即使进程被杀死，系统也会在指定时间唤醒应用。</p>
 * 
 * @author EmptyScreen Team
 * @version 1.0
 */
public class BootReceiver extends BroadcastReceiver {

    /** 自定义启动应用的Action */
    private static final String ACTION_LAUNCH_APP = "com.haier.emptyscreen.ACTION_LAUNCH_APP";
    
    /** PendingIntent请求码 */
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

    private void launchApp(Context context) {
        LogUtils.i("[BootReceiver] Launching app");
        
        try {
            Intent launchIntent = new Intent(context, MainActivity.class);
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
