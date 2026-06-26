package com.haier.emptyscreen;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.webkit.WebView;

import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.PrefsManager;
import com.haier.logger.HLogger;
import com.haier.logger.HLoggerConfig;

/**
 * 应用程序主类 - 管理应用全局状态和生命周期
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>提供全局 Context 访问</li>
 *   <li>初始化日志系统</li>
 *   <li>监控应用生命周期</li>
 *   <li>提供应用重启机制</li>
 * </ul>
 */
public class EmptyScreenApplication extends Application {

    /**
     * 全局单例实例
     */
    private static EmptyScreenApplication sInstance;

    /**
     * 获取应用全局单例
     *
     * @return EmptyScreenApplication 实例
     */
    public static EmptyScreenApplication getInstance() {
        return sInstance;
    }

    /**
     * 应用创建时初始化
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>调用父类 onCreate</li>
     *   <li>保存全局实例引用</li>
     *   <li>初始化日志工具</li>
     *   <li>禁用 WebView 调试模式</li>
     * </ol>
     */
    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        HLoggerConfig loggerConfig = new HLoggerConfig.Builder(this)
                .globalTag("EmptyScreen")
                .addHeader("Device", Build.MODEL)
                .clinicCode("1234567890")
                .addHeader("OS", "Android " + Build.VERSION.RELEASE)
                .build();
        HLogger.init(loggerConfig);
        LogUtils.init(this);
        WebView.setWebContentsDebuggingEnabled(false);
        HLogger.i("[App] Started");
        logBootEnvironment();

        // 将开机关键配置（自定义广播 action、自适应延迟、重试次数/间隔）从 CE 同步到 DE 存储，
        // 供下次 Direct Boot 阶段（用户未解锁）的 BootReceiver 读取。
        // 仅在已解锁时执行；Direct Boot 阶段启动的进程会跳过（无意义）。
        PrefsManager.migrateBootPrefsToDeviceProtectedStorage(this);
    }

    /**
     * 打印进程启动环境快照 - 开机拉起问题的"第一现场"
     *
     * <p>开机拉不起来时，这些环境因子是定位根因的关键：</p>
     * <ul>
     *   <li>userUnlocked：Direct Boot 阶段（解锁前）默认日志目录不可写，最早期日志可能丢失</li>
     *   <li>exactAlarmAllowed：Android 12+ 缺该权限会导致 AlarmManager 重试序列全废</li>
     *   <li>ignoringBatteryOpt：未进电池优化白名单时进程/服务易被 ROM 杀，STR 唤醒拉不起来</li>
     *   <li>processName：确认是哪个进程被拉起（多进程时区分主进程与服务进程）</li>
     * </ul>
     */
    private void logBootEnvironment() {
        try {
            boolean userUnlocked = true;
            // isUserUnlocked() 是 API 24 (N) 才有，低版本（如 Android 5.1）调用会抛
            // NoSuchMethodError，且它属于 Error 无法被下方 catch(Exception) 捕获，会直接崩溃。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.os.UserManager um =
                        (android.os.UserManager) getSystemService(USER_SERVICE);
                if (um != null) {
                    userUnlocked = um.isUserUnlocked();
                }
            }

            boolean exactAlarmAllowed = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.AlarmManager am =
                        (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
                exactAlarmAllowed = am == null || am.canScheduleExactAlarms();
            }

            boolean ignoringBatteryOpt = false;
            // isIgnoringBatteryOptimizations() 是 API 23 (M) 才有，低版本调用同样会抛
            // NoSuchMethodError 导致 onCreate 崩溃，必须做版本判断。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.os.PowerManager pm =
                        (android.os.PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    ignoringBatteryOpt = pm.isIgnoringBatteryOptimizations(getPackageName());
                }
            }

            HLogger.i("[App] BootEnv: androidRelease=" + Build.VERSION.RELEASE
                    + ", sdk=" + Build.VERSION.SDK_INT
                    + ", model=" + Build.MODEL
                    + ", userUnlocked=" + userUnlocked
                    + ", exactAlarmAllowed=" + exactAlarmAllowed
                    + ", ignoringBatteryOpt=" + ignoringBatteryOpt
                    + ", process=" + getProcessNameCompat()
                    + ", pid=" + Process.myPid());
        } catch (Exception e) {
            HLogger.w("[App] logBootEnvironment failed: " + e.getMessage());
        }
    }

    /**
     * 获取当前进程名（兼容各 API 版本）
     */
    private String getProcessNameCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        }
        android.app.ActivityManager am =
                (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am != null && am.getRunningAppProcesses() != null) {
            int pid = Process.myPid();
            for (android.app.ActivityManager.RunningAppProcessInfo p : am.getRunningAppProcesses()) {
                if (p.pid == pid) {
                    return p.processName;
                }
            }
        }
        return "unknown";
    }

    /**
     * 应用终止时调用 - 释放日志资源
     */
    @Override
    public void onTerminate() {
        LogUtils.release();
        super.onTerminate();
    }

    /**
     * 系统内存低时回调
     *
     * <p>系统会在内存不足时调用此方法，应用应在此时释放非必要资源</p>
     */
    @Override
    public void onLowMemory() {
        HLogger.w("[App] onLowMemory");
        super.onLowMemory();
    }

    /**
     * 内存修剪回调 - 根据级别释放资源
     *
     * @param level 内存修剪级别，值越大表示需要释放的资源越多
     *              <ul>
     *                <li>TRIM_MEMORY_RUNNING_MODERATE - 应用正常运行，但系统开始感到压力</li>
     *                <li>TRIM_MEMORY_RUNNING_LOW - 系统内存较低</li>
     *                <li>TRIM_MEMORY_RUNNING_CRITICAL - 系统内存严重不足</li>
     *                <li>TRIM_MEMORY_BACKGROUND - 应用进入后台</li>
     *                <li>TRIM_MEMORY_UI_HIDDEN - UI 不可见</li>
     *              </ul>
     */
    @Override
    public void onTrimMemory(int level) {
        HLogger.d("[App] onTrimMemory level=" + level);
        super.onTrimMemory(level);
    }

    /**
     * 重启应用程序
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>启动 LauncherActivity（清除所有任务栈）</li>
     *   <li>触发垃圾回收</li>
     *   <li>延迟 500ms 后杀死当前进程</li>
     * </ol>
     *
     * <p>注意：此方法会完全重启应用，慎用</p>
     */
    public void restartApp() {
        HLogger.i("[App] Restarting...");

        Intent intent = new Intent(this, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        System.gc();

        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            Process.killProcess(Process.myPid());
        }, 500);
    }
}
