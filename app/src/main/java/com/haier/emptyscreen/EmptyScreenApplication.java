package com.haier.emptyscreen;

import android.app.Application;
import android.content.Intent;
import android.os.Process;
import android.webkit.WebView;

import com.haier.emptyscreen.utils.LogUtils;

public class EmptyScreenApplication extends Application {

    private static EmptyScreenApplication sInstance;

    public static EmptyScreenApplication getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        LogUtils.init(this);
        WebView.setWebContentsDebuggingEnabled(false);
        LogUtils.i("[App] Started");
    }

    @Override
    public void onTerminate() {
        LogUtils.release();
        super.onTerminate();
    }

    @Override
    public void onLowMemory() {
        LogUtils.w("[App] onLowMemory");
        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        LogUtils.d("[App] onTrimMemory level=" + level);
        super.onTrimMemory(level);
    }
    
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
