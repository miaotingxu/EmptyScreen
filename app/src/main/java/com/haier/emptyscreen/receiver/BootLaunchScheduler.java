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
import com.haier.emptyscreen.utils.PrefsManager;
import com.haier.logger.HLogger;

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

    /** 拉起后回查前台状态的延迟（毫秒）- 给系统留出启动 Activity 的时间 */
    private static final long LAUNCH_VERIFY_DELAY_MS = 2500L;

    private BootLaunchScheduler() {
    }

    /**
     * 拉起来源 - 贯穿整条拉起链路的日志，用于区分"哪种开关机/唤醒拉起的"
     */
    public enum LaunchSource {
        /** 断电冷启动：进程全新启动，开机广播紧随进程启动到达 */
        BOOT_COLD,
        /** 开机广播：BOOT_COMPLETED/QUICKBOOT 等（含 ROM 快速开机/热启动） */
        BOOT_BROADCAST,
        /** 待机/休眠唤醒（STR）：SCREEN_ON 判定 CPU 真正挂起过 */
        STR_WAKE
    }

    /**
     * 开机广播触发后调用：记录开机时刻并排定整条重试拉起序列
     *
     * @param context    上下文
     * @param source     拉起来源（断电冷启动 / 开机广播 / STR 唤醒）
     * @param extraInfoMs 附加信息（毫秒）：STR 场景为挂起时长，开机场景为进程启动后耗时；&lt;0 表示无
     */
    public static void onBootDetected(Context context, LaunchSource source, long extraInfoMs) {
        PrefsManager prefs = PrefsManager.getInstance(context);

        // 来源在入口确定并持久化，供后续重试、跨进程的成功点读回
        prefs.saveLaunchSource(source.name());

        // 醒目的触发起点日志：一眼读出本次是哪种开关机/唤醒拉起的
        String extra = extraInfoMs < 0 ? ""
                : (source == LaunchSource.STR_WAKE
                        ? ", suspended=" + (extraInfoMs / 1000) + "s"
                        : ", sinceProcStart=" + extraInfoMs + "ms");
        HLogger.i(TAG + " >>> LAUNCH TRIGGERED [source=" + source.name() + extra + "]");

        // 开机第一时间拉起常驻前台服务，使其动态注册的屏幕广播接收器尽早就位，
        // 让待机/休眠唤醒（str）场景不再依赖 Activity 是否已成功显示
        startForegroundServiceSafely(context);

        // 记录开机基准时刻，重置本次成功标记
        // 注意：Direct Boot 阶段（未解锁）CE 存储写入会被静默丢弃，
        // 这些标记将在用户解锁后由首次成功拉起时回写。
        long bootElapsed = SystemClock.elapsedRealtime();
        prefs.saveLastBootElapsed(bootElapsed);
        prefs.setBootLaunchSuccess(false);

        // 开机关键配置：Direct Boot 阶段 CE 存储不可读，改从 DE 存储读取副本
        boolean userUnlocked = PrefsManager.isUserUnlocked(context);
        int firstDelay = userUnlocked
                ? prefs.getAdaptiveDelaySeconds()
                : PrefsManager.getAdaptiveDelaySecondsDE(context);
        int retryCount = userUnlocked
                ? prefs.getBootRetryCount()
                : PrefsManager.getBootRetryCountDE(context);
        int retryInterval = userUnlocked
                ? prefs.getBootRetryInterval()
                : PrefsManager.getBootRetryIntervalDE(context);

        HLogger.i(TAG + " Boot detected [source=" + source.name() + "] firstDelay=" + firstDelay
                + "s, retryCount=" + retryCount + ", retryInterval=" + retryInterval + "s");

        // 打印后台启动豁免的关键权限状态：缺失时后台 startActivity 会被静默拦截，
        // 这是开机/STR 拉起"广播都正常却不显示"的常见根因。
        logBackgroundLaunchCapability(context);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            HLogger.e(TAG + " AlarmManager null, launching directly");
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
            HLogger.i(TAG + " Scheduled retry#" + retryIndex + " in " + delaySeconds + "s");
        } catch (Exception e) {
            // 区分异常类型：Android 12+ 缺 SCHEDULE_EXACT_ALARM 权限会抛 SecurityException，
            // 会导致整条重试序列全废，需在日志中明确暴露异常类名以便定位
            HLogger.e(TAG + " Failed to schedule retry#" + retryIndex + ": "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
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
        String src = prefs.getLaunchSource();

        if (prefs.isBootLaunchSuccess()) {
            HLogger.i(TAG + " retry#" + retryIndex + " [source=" + src + "] skipped (already launched)");
            return;
        }

        if (isOurAppForeground(context)) {
            HLogger.i(TAG + " retry#" + retryIndex + " [source=" + src + "] skipped (app already foreground)");
            prefs.setBootLaunchSuccess(true);
            return;
        }

        HLogger.i(TAG + " retry#" + retryIndex + " [source=" + src + "] launching app");
        launchNow(context);
    }

    /**
     * 立即拉起 LauncherActivity
     *
     * <p>注意：Android 10+ 从广播/服务后台 startActivity 默认会被系统静默拦截，
     * <b>不抛异常</b>。startActivity 调用"成功"不代表 Activity 真显示了，
     * 因此拉起后延迟回查前台状态，把"静默失败"暴露到日志中。</p>
     */
    private static void launchNow(Context context) {
        try {
            Intent launchIntent = new Intent(context, LauncherActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launchIntent);
            HLogger.i(TAG + " startActivity(LauncherActivity) called");
            verifyLaunchResult(context.getApplicationContext());
        } catch (Exception e) {
            HLogger.e(TAG + " Failed to launch: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * 拉起后延迟回查应用是否真到前台，用于发现后台启动被系统静默拦截的情况
     */
    private static void verifyLaunchResult(Context appContext) {
        final String src = PrefsManager.getInstance(appContext).getLaunchSource();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isOurAppForeground(appContext)) {
                HLogger.i(TAG + " launch verify [source=" + src + "]: app IS foreground (launch succeeded)");
            } else {
                HLogger.e(TAG + " launch verify [source=" + src + "]: app NOT foreground after startActivity"
                        + " -> likely blocked by background-activity-start restriction");
            }
        }, LAUNCH_VERIFY_DELAY_MS);
    }

    /**
     * 打印后台启动 Activity 所需的关键能力状态
     *
     * <p>Android 10+ 默认拦截后台 startActivity（静默失败）。常见豁免途径：
     * 持有悬浮窗权限（SYSTEM_ALERT_WINDOW）。这里打印该权限状态，
     * 缺失即可解释"广播/服务都正常却拉不起界面"。</p>
     */
    private static void logBackgroundLaunchCapability(Context context) {
        boolean canDrawOverlays = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            canDrawOverlays = android.provider.Settings.canDrawOverlays(context);
        }
        if (canDrawOverlays) {
            HLogger.i(TAG + " bgLaunch capability: canDrawOverlays=true (background start allowed)");
        } else {
            HLogger.e(TAG + " bgLaunch capability: canDrawOverlays=FALSE"
                    + " -> background startActivity will likely be blocked; grant 'display over other apps'");
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
            HLogger.i(TAG + " ForegroundService start requested");
        } catch (Exception e) {
            HLogger.e(TAG + " Failed to start ForegroundService: " + e.getMessage());
        }
    }

    /**
     * 探测我们的应用是否已在前台（避免无意义的重复拉起）
     *
     * <p>供 {@link ForegroundService} 在 onCreate 中探测真实前台状态作为
     * {@code mIsAppInForeground} 初值，避免服务被系统重启拉起后默认 true
     * 导致首次 SCREEN_ON 被误判为"已在前台"而漏掉 STR 唤醒。</p>
     */
    public static boolean isOurAppForeground(Context context) {
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
            HLogger.w(TAG + " isOurAppForeground check failed: " + e.getMessage());
        }
        return false;
    }
}
