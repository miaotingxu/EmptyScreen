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
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.haier.emptyscreen.service.ForegroundService;
import com.haier.emptyscreen.utils.NetworkUtils;
import com.haier.emptyscreen.utils.PrefsManager;
import com.haier.emptyscreen.webview.CustomWebChromeClient;
import com.haier.emptyscreen.webview.CustomWebViewClient;
import com.haier.emptyscreen.webview.WebViewPerformanceManager;
import com.haier.logger.HLogger;

public class MainActivity extends Activity {

    private static final String TAG = "[MainActivity]";
    private static final int REQUEST_VIDEO_PICKER = 1001;
    private static final int REQUEST_SETTINGS = 1003;

    private boolean mNeedReloadAfterCacheClear = false;

    private WebView mWebView;
    private ProgressBar mProgressBar;
    private View mErrorLayout;

    private PrefsManager mPrefsManager;
    private Handler mHandler;
    private CustomWebViewClient mWebViewClient;
    private CustomWebChromeClient mWebChromeClient;
    private WebViewPerformanceManager mPerformanceManager;

    private boolean mIsAppInForeground = true;
    private boolean mIsWebViewPaused = false;

    /** 当前 WebView 已加载的 URL，用于在返回首页时判断地址是否被设置页改动过 */
    private String mLoadedUrl = null;

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

        // === P1-3: UA / Cookie / 调试开关 ===

        // 移除默认 UA 末尾的 "; wv" 标识。
        // 许多站点会根据该标识识别为"应用内 WebView"并返回精简版/受限版页面（隐藏功能、强制跳转 App 协议等），
        // 与"PC 浏览器能显示的，Android 也要能显示"的目标相悖。去除后站点将按普通 Chrome 移动版处理。
        String defaultUA = settings.getUserAgentString();
        if (defaultUA != null && defaultUA.contains("; wv")) {
            String tweakedUA = defaultUA.replace("; wv", "");
            settings.setUserAgentString(tweakedUA);
            HLogger.d(TAG + " UA tweaked (removed ; wv): " + tweakedUA);
        }

        // 启用第三方 Cookie：嵌入式 iframe / CDN 资源 / 跨域登录场景需要，
        // PC 浏览器默认允许，WebView 自 Lollipop 起默认禁用，需显式开启。
        // 注意：Android 8.0+ 上 setAcceptCookie 仍默认为 true，但第三方 Cookie 必须单独开。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);
        }

        // 仅 debug 构建开启 WebView 远程调试（chrome://inspect），release 包自动关闭，
        // 避免线上设备被任意调试工具注入 JS。
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        // 启用多窗口支持：让 window.open() / target="_blank" 触发 onCreateWindow 回调，
        // 而非被静默丢弃（PC 浏览器原生支持新窗口，WebView 默认不处理）
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        mWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        mPerformanceManager = new WebViewPerformanceManager(mWebView);
        mPerformanceManager.optimizeWebViewPerformance();

        mWebViewClient = new CustomWebViewClient(this, mWebView);
        // 页面加载错误（网络/HTTP/SSL 取消）时显示本地错误页，
        // 页面加载成功时隐藏错误页 - 让用户在加载失败时有可视反馈而非白屏
        mWebViewClient.setOnPageStateListener(new CustomWebViewClient.OnPageStateListener() {
            @Override
            public void onPageError(String description) {
                showErrorView();
            }

            @Override
            public void onPageLoaded() {
                hideErrorView();
            }
        });
        mWebView.setWebViewClient(mWebViewClient);

        // 下载链接处理：WebView 默认不处理下载，PC 浏览器会触发系统下载。
        // 这里把下载请求交给系统下载管理器或外部浏览器处理。
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            HLogger.i(TAG + " Download requested: " + url + " mime=" + mimetype);
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                HLogger.e(TAG + " Failed to handle download: " + e.getMessage());
                Toast.makeText(this, "无可用应用处理此下载链接", Toast.LENGTH_SHORT).show();
            }
        });

        FrameLayout videoContainer = findViewById(R.id.video_container);
        mWebChromeClient = new CustomWebChromeClient(this, mWebView, videoContainer);
        mWebView.setWebChromeClient(mWebChromeClient);
    }

    private void loadUrl() {
        String url = mPrefsManager.getUrl();

        if (!NetworkUtils.isValidUrl(url)) {
            HLogger.e(TAG + " Invalid URL: " + url);
            showErrorView();
            return;
        }

        hideErrorView();
        mWebView.loadUrl(url);
        mLoadedUrl = url;
        HLogger.i(TAG + " Loading URL: " + url);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, REQUEST_SETTINGS);
    }

    private void showVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择视频文件"), REQUEST_VIDEO_PICKER);
        } catch (Exception e) {
            Toast.makeText(this, "请先安装文件管理器", Toast.LENGTH_SHORT).show();
            HLogger.e(TAG + " Failed to open video picker: " + e.getMessage());
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

        // 自愈常驻前台服务：电视 ROM 可能在 onTaskRemoved/低内存时杀掉服务，
        // 导致其动态注册的 SCREEN_ON 接收器消失、STR 唤醒失效。
        // 每次 Activity 回到前台时确保服务存活，让 STR 唤醒链路自愈。
        ForegroundService.ensureRunning(this);

        if (mWebView != null && mIsWebViewPaused) {
            mWebView.onResume();
            mIsWebViewPaused = false;
        }

        // 从设置页返回时，若地址被改动则立即加载新网页
        reloadIfUrlChanged();
    }

    /** 检查设置页是否改动了地址，若改动则重新加载首页网页 */
    private void reloadIfUrlChanged() {
        if (mPrefsManager == null) {
            return;
        }

        if (mNeedReloadAfterCacheClear) {
            HLogger.i(TAG + " Browser cache cleared, reloading page");
            loadUrl();
            mNeedReloadAfterCacheClear = false;
            return;
        }

        String latestUrl = mPrefsManager.getUrl();
        if (latestUrl != null && !latestUrl.equals(mLoadedUrl)) {
            HLogger.i(TAG + " URL changed, reloading: " + mLoadedUrl + " -> " + latestUrl);
            loadUrl();
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
        // 全屏视频播放时，返回键优先退出全屏，而非回退网页历史
        if (mWebChromeClient != null && mWebChromeClient.exitFullscreenIfActive()) {
            return;
        }
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
        } else if (requestCode == CustomWebChromeClient.FILE_CHOOSER_REQUEST_CODE) {
            if (mWebChromeClient != null) {
                mWebChromeClient.handleFileChooserResult(resultCode, data);
            }
        } else if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) {
            mNeedReloadAfterCacheClear = true;
        }
    }

    private void playVideo(Uri videoUri) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, videoUri.toString());
        intent.putExtra(VideoPlayerActivity.EXTRA_IS_LOCAL, true);
        startActivity(intent);
        HLogger.i(TAG + " Playing video: " + videoUri.toString());
    }
}
