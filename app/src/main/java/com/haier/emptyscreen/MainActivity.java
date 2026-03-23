package com.haier.emptyscreen;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.NetworkUtils;
import com.haier.emptyscreen.utils.PrefsManager;
import com.haier.emptyscreen.webview.CustomWebViewClient;
import com.haier.emptyscreen.webview.WebViewPerformanceManager;

public class MainActivity extends Activity {

    private static final String TAG = "[MainActivity]";
    private static final int REQUEST_VIDEO_PICKER = 1001;

    private WebView mWebView;
    private ProgressBar mProgressBar;
    private View mErrorLayout;

    private PrefsManager mPrefsManager;
    private Handler mHandler;
    private CustomWebViewClient mWebViewClient;
    private WebViewPerformanceManager mPerformanceManager;

    private boolean mIsAppInForeground = true;
    private boolean mIsWebViewPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefsManager = PrefsManager.getInstance(this);
        mHandler = new Handler(Looper.getMainLooper());

        initializeViews();
        setupWebView();
        loadUrl();
    }

    private void initializeViews() {
        mWebView = findViewById(R.id.webview);
        mProgressBar = findViewById(R.id.progress_bar);
        mErrorLayout = findViewById(R.id.error_layout);

        findViewById(R.id.btn_retry).setOnClickListener(v -> retryLoading());
        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());
    }

    private void setupWebView() {
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        mWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        mPerformanceManager = new WebViewPerformanceManager(mWebView);
        mPerformanceManager.optimizeWebViewPerformance();

        mWebViewClient = new CustomWebViewClient(this, mWebView, null);
        mWebView.setWebViewClient(mWebViewClient);
        mWebView.setWebChromeClient(new WebChromeClient());
    }

    private void loadUrl() {
        String url = mPrefsManager.getUrl();

        if (!NetworkUtils.isValidUrl(url)) {
            LogUtils.e(TAG + " Invalid URL: " + url);
            showErrorView();
            return;
        }

        hideErrorView();
        mWebView.loadUrl(url);
        LogUtils.i(TAG + " Loading URL: " + url);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void showVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择视频文件"), REQUEST_VIDEO_PICKER);
        } catch (Exception e) {
            Toast.makeText(this, "请先安装文件管理器", Toast.LENGTH_SHORT).show();
            LogUtils.e(TAG + " Failed to open video picker: " + e.getMessage());
        }
    }

    private void hideErrorView() {
        mErrorLayout.setVisibility(View.GONE);
        mWebView.setVisibility(View.VISIBLE);
    }

    private void showErrorView() {
        mErrorLayout.setVisibility(View.VISIBLE);
        mWebView.setVisibility(View.GONE);
    }

    private void retryLoading() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showErrorView();
            Toast.makeText(this, "网络不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        loadUrl();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showVideoPicker();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsAppInForeground = true;

        if (mWebView != null && mIsWebViewPaused) {
            mWebView.onResume();
            mIsWebViewPaused = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsAppInForeground = false;

        if (mWebView != null && !mIsWebViewPaused) {
            mWebView.onPause();
            mIsWebViewPaused = true;
        }
    }

    @Override
    protected void onDestroy() {
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_VIDEO_PICKER && resultCode == RESULT_OK && data != null) {
            Uri videoUri = data.getData();
            if (videoUri != null) {
                playVideo(videoUri);
            }
        }
    }

    private void playVideo(Uri videoUri) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, videoUri.toString());
        intent.putExtra(VideoPlayerActivity.EXTRA_IS_LOCAL, true);
        startActivity(intent);
        LogUtils.i(TAG + " Playing video: " + videoUri.toString());
    }
}
