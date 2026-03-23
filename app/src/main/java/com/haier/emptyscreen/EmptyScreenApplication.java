package com.haier.emptyscreen;

import android.app.Application;
import android.content.Intent;
import android.os.Process;
import android.webkit.WebView;

import com.haier.emptyscreen.utils.LogUtils;

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

    /** 全局单例实例 */
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
        LogUtils.init(this);
        WebView.setWebContentsDebuggingEnabled(false);
        LogUtils.i("[App] Started");
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
        LogUtils.w("[App] onLowMemory");
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
        LogUtils.d("[App] onTrimMemory level=" + level);
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
        LogUtils.i("[App] Restarting...");
        
        Intent intent = new Intent(this, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        
        System.gc();
        
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            Process.killProcess(Process.myPid());
        }, 500);
    }
}
