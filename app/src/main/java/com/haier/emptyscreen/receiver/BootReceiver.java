package com.haier.emptyscreen.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.text.TextUtils;

import com.haier.emptyscreen.utils.PrefsManager;
import com.haier.logger.HLogger;

/**
 * 开机启动接收器 - 监听多种开机/唤醒广播，触发自适应重试拉起
 *
 * <p>监听的广播来源分三类：</p>
 * <ul>
 *   <li>标准系统广播：BOOT_COMPLETED / LOCKED_BOOT_COMPLETED / QUICKBOOT_POWERON / REBOOT 等</li>
 *   <li>厂商私有广播：海尔/海信等 ROM 自定义 action，通过 {@link PrefsManager} 可配置补充</li>
 *   <li>内部重试广播：{@link BootLaunchScheduler#ACTION_RETRY_LAUNCH}</li>
 * </ul>
 *
 * <p>真正的"延迟多久、重试几次"逻辑全部委托给 {@link BootLaunchScheduler}，
 * 以适配不同厂商不同机型差异巨大的"开机进首页"时间。</p>
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "[BootReceiver]";

    /** 类加载（≈进程启动）时刻的 elapsedRealtime，用于估算"广播在进程启动后多久到达" */
    private static final long PROCESS_START_ELAPSED = SystemClock.elapsedRealtime();

    /** 冷启动判定阈值（毫秒）：广播在进程启动后此时间内到达，视为随进程冷启动（断电开机） */
    private static final long COLD_BOOT_THRESHOLD_MS = 8000L;

    /** 内置识别的开机/唤醒类 action（标准 + 常见厂商快速开机）
     *  注：SCREEN_ON/SCREEN_OFF 系统不允许静态注册，由 ForegroundService 动态注册处理 */
    private static final String[] BUILTIN_BOOT_ACTIONS = {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.REBOOT",
            "android.intent.action.USER_PRESENT"
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        // 无条件落盘：进程启动后第一现场。即使后面任何分支提前 return，
        // 也能从日志确认"广播到底有没有送达本应用"——这是区分
        // "广播没来" vs "来了但拉起失败"的分水岭。
        long sinceProcStart = SystemClock.elapsedRealtime() - PROCESS_START_ELAPSED;
        String action = (intent == null) ? null : intent.getAction();
        boolean userUnlocked = PrefsManager.isUserUnlocked(context);
        HLogger.i(TAG + " onReceive action=" + action
                + ", sinceProcStart=" + sinceProcStart + "ms"
                + ", userUnlocked=" + userUnlocked
                + ", pid=" + android.os.Process.myPid());

        if (intent == null || action == null) {
            HLogger.w(TAG + " Received null intent or action, ignored");
            return;
        }

        // 内部重试广播：交给调度器执行单次拉起
        if (BootLaunchScheduler.ACTION_RETRY_LAUNCH.equals(action)) {
            int retryIndex = intent.getIntExtra(BootLaunchScheduler.EXTRA_RETRY_INDEX, -1);
            HLogger.i(TAG + " Internal retry broadcast, retryIndex=" + retryIndex);
            BootLaunchScheduler.handleRetry(context, retryIndex);
            return;
        }

        // 开机/唤醒类广播：启动整条自适应重试序列
        if (isBootAction(context, action)) {
            // 用"进程启动后多久收到广播"区分冷启动与广播热启动：
            // 极小说明进程随广播全新拉起（断电冷启动），较大说明进程早已在跑（ROM 热启动/普通开机广播）。
            BootLaunchScheduler.LaunchSource source =
                    sinceProcStart < COLD_BOOT_THRESHOLD_MS
                            ? BootLaunchScheduler.LaunchSource.BOOT_COLD
                            : BootLaunchScheduler.LaunchSource.BOOT_BROADCAST;
            HLogger.i(TAG + " Boot/wake action accepted: " + action
                    + " -> onBootDetected [source=" + source.name() + "]");
            BootLaunchScheduler.onBootDetected(context, source, sinceProcStart);
        } else {
            HLogger.w(TAG + " action not recognized as boot/wake, ignored: " + action);
        }
    }

    /**
     * 判断收到的 action 是否属于"开机/唤醒"类
     *
     * <p>先匹配内置列表，再匹配用户在 {@link PrefsManager} 配置的厂商私有 action，
     * 这样无需改代码即可通过真机抓包补充新机型的私有广播。</p>
     *
     * <p>Direct Boot 阶段（用户未解锁）CE 存储不可访问，此时从 DE 存储读取
     * 自定义 action 的副本（由 {@link PrefsManager#migrateBootPrefsToDeviceProtectedStorage}
     * 在上次解锁后同步）。</p>
     *
     * @param context 上下文
     * @param action  收到的广播 action
     * @return true-属于开机/唤醒类
     */
    private boolean isBootAction(Context context, String action) {
        for (String builtin : BUILTIN_BOOT_ACTIONS) {
            if (builtin.equals(action)) {
                return true;
            }
        }

        // Direct Boot 阶段 CE 存储不可访问，改从 DE 存储读取自定义 action 副本
        String custom;
        if (PrefsManager.isUserUnlocked(context)) {
            custom = PrefsManager.getInstance(context).getCustomBootActions();
        } else {
            custom = PrefsManager.getCustomBootActionsDE(context);
            HLogger.d(TAG + " Direct Boot: reading custom boot actions from DE storage");
        }

        if (!TextUtils.isEmpty(custom)) {
            for (String item : custom.split(",")) {
                if (action.equals(item.trim())) {
                    HLogger.i(TAG + " Matched custom boot action: " + action);
                    return true;
                }
            }
        }
        return false;
    }
}
