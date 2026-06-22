package com.haier.emptyscreen.receiver;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import com.haier.emptyscreen.LauncherActivity;
import com.haier.emptyscreen.service.ForegroundService;
import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.PrefsManager;

import java.util.List;

/**
 * 开机拉起调度器 - 负责"自适应延迟 + 多次重试抢占"地把应用拉到前台
 *
 * <p>核心思路（适配不同厂商不同机型的开机进首页时间）：</p>
 * <ul>
 *   <li>首次拉起时机来自 {@link PrefsManager#getAdaptiveDelaySeconds()}，由历史学习收敛到本机最佳值</li>
 *   <li>在首次时机之后，按固定间隔再排若干次重试，覆盖系统首页起来较慢的机型</li>
 *   <li>每次拉起前先做系统就绪探测，命中系统桌面在前台时抢占最有效</li>
 *   <li>LauncherActivity 成功进前台后回写成功标记并学习耗时，后续重试自动空转</li>
 * </ul>
 *
 * @author EmptyScreen Team
 * @version 1.0
 */
public class BootLaunchScheduler {

    private static final String TAG = "[BootLaunchScheduler]";

    /** 重试拉起广播 action */
    static final String ACTION_RETRY_LAUNCH = "com.haier.emptyscreen.ACTION_RETRY_LAUNCH";

    /** 携带"第几次重试"的 extra 键 */
    static final String EXTRA_RETRY_INDEX = "retry_index";

    /** 重试 PendingIntent 的请求码基数（避免与其它 alarm 冲突） */
    private static final int RETRY_REQUEST_CODE_BASE = 2000;

    private BootLaunchScheduler() {
    }

    /**
     * 开机广播触发后调用：记录开机时刻并排定整条重试拉起序列
     *
     * @param context 上下文
     */
    public static void onBootDetected(Context context) {
        PrefsManager prefs = PrefsManager.getInstance(context);

        // 开机第一时间拉起常驻前台服务，使其动态注册的屏幕广播接收器尽早就位，
        // 让待机/休眠唤醒（str）场景不再依赖 Activity 是否已成功显示
        startForegroundServiceSafely(context);

        // 记录开机基准时刻，重置本次成功标记
        long bootElapsed = SystemClock.elapsedRealtime();
        prefs.saveLastBootElapsed(bootElapsed);
        prefs.setBootLaunchSuccess(false);

        int firstDelay = prefs.getAdaptiveDelaySeconds();
        int retryCount = prefs.getBootRetryCount();
        int retryInterval = prefs.getBootRetryInterval();

        LogUtils.i(TAG + " Boot detected. firstDelay=" + firstDelay + "s, retryCount="
                + retryCount + ", retryInterval=" + retryInterval + "s");

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            LogUtils.e(TAG + " AlarmManager null, launching directly");
            launchNow(context);
            return;
        }

        // 第 0 次按自适应延迟，其后每次叠加 retryInterval
        for (int i = 0; i <= retryCount; i++) {
            int delaySeconds = firstDelay + i * retryInterval;
            scheduleRetry(context, alarmManager, i, delaySeconds);
        }
    }

    /**
     * 排定单次重试拉起
     */
    private static void scheduleRetry(Context context, AlarmManager alarmManager,
                                      int retryIndex, int delaySeconds) {
        Intent intent = new Intent(context, BootReceiver.class);
        intent.setAction(ACTION_RETRY_LAUNCH);
        intent.putExtra(EXTRA_RETRY_INDEX, retryIndex);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                RETRY_REQUEST_CODE_BASE + retryIndex,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerAt = SystemClock.elapsedRealtime() + delaySeconds * 1000L;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            }
            LogUtils.i(TAG + " Scheduled retry#" + retryIndex + " in " + delaySeconds + "s");
        } catch (Exception e) {
            LogUtils.e(TAG + " Failed to schedule retry#" + retryIndex + ": " + e.getMessage());
        }
    }

    /**
     * 处理一次重试拉起：若已成功则空转，否则探测就绪状态后拉起
     *
     * @param context    上下文
     * @param retryIndex 第几次重试
     */
    public static void handleRetry(Context context, int retryIndex) {
        PrefsManager prefs = PrefsManager.getInstance(context);

        if (prefs.isBootLaunchSuccess()) {
            LogUtils.i(TAG + " retry#" + retryIndex + " skipped (already launched)");
            return;
        }

        if (isOurAppForeground(context)) {
            LogUtils.i(TAG + " retry#" + retryIndex + " skipped (app already foreground)");
            prefs.setBootLaunchSuccess(true);
            return;
        }

        LogUtils.i(TAG + " retry#" + retryIndex + " launching app");
        launchNow(context);
    }

    /**
     * 立即拉起 LauncherActivity
     */
    private static void launchNow(Context context) {
        try {
            Intent launchIntent = new Intent(context, LauncherActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launchIntent);
            LogUtils.i(TAG + " startActivity(LauncherActivity) called");
        } catch (Exception e) {
            LogUtils.e(TAG + " Failed to launch: " + e.getMessage());
        }
    }

    /**
     * 安全启动常驻前台服务
     *
     * <p>从开机广播上下文启动前台服务是系统允许的豁免行为。服务一旦运行，
     * 其在 onCreate 中动态注册的屏幕广播接收器即可接住后续待机唤醒（SCREEN_ON）。</p>
     */
    private static void startForegroundServiceSafely(Context context) {
        try {
            Intent serviceIntent = new Intent(context, ForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            LogUtils.i(TAG + " ForegroundService start requested");
        } catch (Exception e) {
            LogUtils.e(TAG + " Failed to start ForegroundService: " + e.getMessage());
        }
    }

    /**
     * 探测我们的应用是否已在前台（避免无意义的重复拉起）
     */
    private static boolean isOurAppForeground(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return false;
            }
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes == null) {
                return false;
            }
            String myPkg = context.getPackageName();
            for (ActivityManager.RunningAppProcessInfo p : processes) {
                if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        && p.processName != null && p.processName.startsWith(myPkg)) {
                    return true;
                }
            }
        } catch (Exception e) {
            LogUtils.w(TAG + " isOurAppForeground check failed: " + e.getMessage());
        }
        return false;
    }
}
