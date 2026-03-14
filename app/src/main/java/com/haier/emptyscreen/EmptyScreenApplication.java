package com.haier.emptyscreen;

import android.app.Application;
import android.webkit.WebView;

import com.haier.emptyscreen.utils.LogUtils;

public class EmptyScreenApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtils.init(this);
        LogUtils.i("[EmptyScreenApplication] onCreate");

        WebView.setWebContentsDebuggingEnabled(false);
    }

    @Override
    public void onTerminate() {
        LogUtils.i("[EmptyScreenApplication] onTerminate");
        LogUtils.release();
        super.onTerminate();
    }

    @Override
    public void onLowMemory() {
        LogUtils.w("[EmptyScreenApplication] onLowMemory");
        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        LogUtils.d("[EmptyScreenApplication] onTrimMemory level: " + level);
        super.onTrimMemory(level);
    }
}
