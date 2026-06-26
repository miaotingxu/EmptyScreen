package com.haier.emptyscreen.webview;

import android.app.Activity;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import com.haier.logger.HLogger;

/**
 * WebViewClient 实现 - 处理 WebView 内容加载层回调
 *
 * <p>职责：URL 拦截、SSL 证书错误处理（医疗内网自签名场景）、
 * 页面加载错误回调（触发错误页显示/隐藏）。</p>
 */
public class CustomWebViewClient extends android.webkit.WebViewClient {

    private static final String TAG = "[CustomWebViewClient]";

    private final Activity mActivity;
    private final WebView mWebView;

    /** 页面加载错误/恢复回调，由 MainActivity 实现以控制错误页显示 */
    private OnPageStateListener mPageStateListener;

    public CustomWebViewClient(Activity activity, WebView webView) {
        mActivity = activity;
        mWebView = webView;
    }

    public void setOnPageStateListener(OnPageStateListener listener) {
        mPageStateListener = listener;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        HLogger.d(TAG + " Loading URL: " + url);
        return false;
    }

    /**
     * SSL 证书错误处理
     *
     * <p>PC 浏览器对自签名证书会显示警告页让用户选择是否继续；WebView 默认直接阻塞。
     * 医疗内网常使用自签名证书，这里弹对话框让用户决定是否继续，
     * 兼顾安全性与内网可用性。</p>
     */
    @Override
    public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
        HLogger.w(TAG + " onReceivedSslError: url=" + error.getUrl()
                + " primaryError=" + error.getPrimaryError());

        if (mActivity.isFinishing() || mActivity.isDestroyed()) {
            handler.cancel();
            return;
        }

        new android.app.AlertDialog.Builder(mActivity)
                .setTitle("SSL 证书错误")
                .setMessage("该网站的证书不受信任。是否继续访问？\n\n" + error.getUrl())
                .setPositiveButton("继续", (d, w) -> handler.proceed())
                .setNegativeButton("取消", (d, w) -> handler.cancel())
                .setOnCancelListener(d -> handler.cancel())
                .show();
    }

    /**
     * 页面资源加载失败（网络断开、DNS 解析失败等）
     *
     * <p>仅主框架错误才触发错误页，子资源（图片/CSS/JS）失败不影响整体页面显示。</p>
     */
    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        HLogger.e(TAG + " onReceivedError: code=" + errorCode + " desc=" + description
                + " url=" + failingUrl);
        if (mPageStateListener != null) {
            mPageStateListener.onPageError(description);
        }
    }

    /**
     * HTTP 错误（404/500 等）
     *
     * <p>仅主框架的 HTTP 错误才显示错误页，避免子资源 404 触发误报。</p>
     */
    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        int statusCode = errorResponse.getStatusCode();
        HLogger.w(TAG + " onReceivedHttpError: " + statusCode + " url=" + request.getUrl());
        if (request.isForMainFrame() && mPageStateListener != null) {
            mPageStateListener.onPageError("HTTP " + statusCode);
        }
    }

    /**
     * 页面加载完成 - 隐藏错误页（若有）
     */
    @Override
    public void onPageFinished(WebView view, String url) {
        HLogger.d(TAG + " onPageFinished: " + url);
        if (mPageStateListener != null) {
            mPageStateListener.onPageLoaded();
        }
    }

    /**
     * 页面加载状态回调接口
     */
    public interface OnPageStateListener {
        /** 页面加载出错（网络/HTTP），应显示错误页 */
        void onPageError(String description);

        /** 页面加载成功，应隐藏错误页 */
        void onPageLoaded();
    }
}
