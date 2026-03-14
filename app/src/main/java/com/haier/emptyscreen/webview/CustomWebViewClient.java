package com.haier.emptyscreen.webview;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.haier.emptyscreen.utils.LogUtils;

public class CustomWebViewClient extends WebViewClient {
    
    private final Context mContext;
    private final ProgressBar mProgressBar;
    private final LinearLayout mErrorLayout;
    private final TextView mTvErrorMessage;
    private final Button mBtnRetry;
    private final WebViewErrorCallback mCallback;
    
    private boolean mHasError = false;
    
    public interface WebViewErrorCallback {
        void onRetry();
    }
    
    public CustomWebViewClient(Context context, ProgressBar progressBar, 
                               LinearLayout errorLayout, TextView tvErrorMessage,
                               Button btnRetry, WebViewErrorCallback callback) {
        mContext = context;
        mProgressBar = progressBar;
        mErrorLayout = errorLayout;
        mTvErrorMessage = tvErrorMessage;
        mBtnRetry = btnRetry;
        mCallback = callback;
        
        if (mBtnRetry != null && mCallback != null) {
            mBtnRetry.setOnClickListener(v -> {
                hideError();
                mCallback.onRetry();
            });
        }
    }
    
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        LogUtils.d("[CustomWebViewClient] shouldOverrideUrlLoading: " + url);
        
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false;
        }
        
        LogUtils.w("[CustomWebViewClient] Blocked non-http(s) URL: " + url);
        return true;
    }
    
    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        LogUtils.i("[CustomWebViewClient] Page started loading: " + url);
        mHasError = false;
        hideError();
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.VISIBLE);
            mProgressBar.setProgress(0);
        }
    }
    
    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        LogUtils.i("[CustomWebViewClient] Page finished loading: " + url);
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.GONE);
        }
        if (!mHasError) {
            hideError();
        }
    }
    
    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        
        if (request.isForMainFrame()) {
            mHasError = true;
            String errorMessage = "页面加载失败";
            if (error.getDescription() != null) {
                errorMessage = error.getDescription().toString();
            }
            LogUtils.e("[CustomWebViewClient] Received error for main frame: " + errorMessage);
            showError(errorMessage);
        }
    }
    
    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        super.onReceivedHttpError(view, request, errorResponse);
        
        if (request.isForMainFrame()) {
            mHasError = true;
            int statusCode = errorResponse.getStatusCode();
            String reason = errorResponse.getReasonPhrase();
            String errorMessage = "HTTP错误: " + statusCode;
            if (reason != null) {
                errorMessage += " - " + reason;
            }
            LogUtils.e("[CustomWebViewClient] Received HTTP error: " + statusCode + " " + reason);
            showError(errorMessage);
        }
    }
    
    private void showError(String message) {
        if (mContext instanceof Activity) {
            ((Activity) mContext).runOnUiThread(() -> {
                if (mErrorLayout != null) {
                    mErrorLayout.setVisibility(View.VISIBLE);
                }
                if (mTvErrorMessage != null) {
                    mTvErrorMessage.setText(message);
                }
            });
        }
    }
    
    private void hideError() {
        if (mContext instanceof Activity) {
            ((Activity) mContext).runOnUiThread(() -> {
                if (mErrorLayout != null) {
                    mErrorLayout.setVisibility(View.GONE);
                }
            });
        }
    }
    
    public boolean hasError() {
        return mHasError;
    }
}
