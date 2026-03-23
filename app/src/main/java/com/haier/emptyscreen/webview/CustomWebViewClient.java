package com.haier.emptyscreen.webview;

import android.app.Activity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.haier.emptyscreen.utils.LogUtils;

public class CustomWebViewClient extends android.webkit.WebViewClient {

    private static final String TAG = "[CustomWebViewClient]";

    private Activity mActivity;
    private WebView mWebView;
    private FrameLayout mVideoContainer;
    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;

    public CustomWebViewClient(Activity activity, WebView webView, FrameLayout videoContainer) {
        mActivity = activity;
        mWebView = webView;
        mVideoContainer = videoContainer;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        LogUtils.d(TAG + " Loading URL: " + url);
        return false;
    }

    public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        if (mCustomView != null) {
            callback.onCustomViewHidden();
            return;
        }

        if (mVideoContainer == null) {
            callback.onCustomViewHidden();
            return;
        }

        mCustomView = view;
        mCustomViewCallback = callback;

        mVideoContainer.addView(view);
        mVideoContainer.setVisibility(View.VISIBLE);

        setSystemUiVisibility(true);
        
        LogUtils.d(TAG + " Entered fullscreen mode");
    }

    public void onHideCustomView() {
        if (mCustomView == null) {
            return;
        }

        if (mVideoContainer != null) {
            mVideoContainer.removeView(mCustomView);
            mVideoContainer.setVisibility(View.GONE);
        }
        mCustomView = null;
        mCustomViewCallback.onCustomViewHidden();

        setSystemUiVisibility(false);
        
        LogUtils.d(TAG + " Exited fullscreen mode");
    }

    private void setSystemUiVisibility(boolean fullscreen) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

            if (fullscreen) {
                visibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }

            mActivity.getWindow().getDecorView().setSystemUiVisibility(visibility);
        }
    }
}
