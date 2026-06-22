package com.haier.emptyscreen.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.PrefsManager;

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
        if (intent == null || intent.getAction() == null) {
            LogUtils.w(TAG + " Received null intent or action");
            return;
        }

        String action = intent.getAction();
        LogUtils.i(TAG + " Received broadcast: " + action);

        // 内部重试广播：交给调度器执行单次拉起
        if (BootLaunchScheduler.ACTION_RETRY_LAUNCH.equals(action)) {
            int retryIndex = intent.getIntExtra(BootLaunchScheduler.EXTRA_RETRY_INDEX, -1);
            BootLaunchScheduler.handleRetry(context, retryIndex);
            return;
        }

        // 开机/唤醒类广播：启动整条自适应重试序列
        if (isBootAction(context, action)) {
            BootLaunchScheduler.onBootDetected(context);
        }
    }

    /**
     * 判断收到的 action 是否属于"开机/唤醒"类
     *
     * <p>先匹配内置列表，再匹配用户在 {@link PrefsManager} 配置的厂商私有 action，
     * 这样无需改代码即可通过真机抓包补充新机型的私有广播。</p>
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

        String custom = PrefsManager.getInstance(context).getCustomBootActions();
        if (!TextUtils.isEmpty(custom)) {
            for (String item : custom.split(",")) {
                if (action.equals(item.trim())) {
                    LogUtils.i(TAG + " Matched custom boot action: " + action);
                    return true;
                }
            }
        }
        return false;
    }
}
