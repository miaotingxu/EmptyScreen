package com.haier.emptyscreen.webview;

import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.haier.emptyscreen.utils.LogUtils;

/**
 * WebView 性能管理器 - 优化 WebView 性能和内存使用
 */
public class WebViewPerformanceManager {

    private static final String TAG = "[WebViewPerformanceManager]";

    private WebView mWebView;

    public WebViewPerformanceManager(WebView webView) {
        mWebView = webView;
    }

    /**
     * 优化 WebView 性能配置
     */
    public void optimizeWebViewPerformance() {
        enableHardwareAcceleration();
        configureCache();
        configureDrawingCache();
        configureGeolocation();
        configureMediaPlayback();
        
        LogUtils.d(TAG + " WebView performance optimization completed");
    }

    /**
     * 启用硬件加速
     */
    private void enableHardwareAcceleration() {
        mWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        LogUtils.d(TAG + " Hardware acceleration enabled");
    }

    /**
     * 配置缓存设置
     */
    private void configureCache() {
        WebSettings settings = mWebView.getSettings();
//        settings.setAppCacheEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        LogUtils.d(TAG + " Cache configured");
    }

    /**
     * 配置绘图缓存
     */
    private void configureDrawingCache() {
        mWebView.setDrawingCacheEnabled(false);
        LogUtils.d(TAG + " Drawing cache disabled");
    }

    /**
     * 配置地理位置权限
     */
    private void configureGeolocation() {
        WebSettings settings = mWebView.getSettings();
        settings.setGeolocationEnabled(true);
        LogUtils.d(TAG + " Geolocation enabled");
    }

    /**
     * 配置媒体播放（允许后台播放）
     */
    private void configureMediaPlayback() {
        WebSettings settings = mWebView.getSettings();
        settings.setMediaPlaybackRequiresUserGesture(false);
        LogUtils.d(TAG + " Media playback configured for background play");
    }

    /**
     * 释放 WebView 资源
     */
    public void release() {
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
            LogUtils.d(TAG + " WebView resources released");
        }
    }
}
