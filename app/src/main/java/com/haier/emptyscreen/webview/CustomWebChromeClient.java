package com.haier.emptyscreen.webview;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.haier.logger.HLogger;

import java.util.Arrays;

/**
 * WebChromeClient 实现 - 处理 WebView 的浏览器宿主层回调
 *
 * <p>职责：HTML5 video 全屏、{@code <input type="file">} 文件选择、{@code window.open()} 新窗口、
 * Web API 权限请求（摄像头/麦克风/地理位置）、console 日志转发。
 * 这些方法属于 {@link WebChromeClient} 而非 {@link android.webkit.WebViewClient}，
 * 必须设置到 {@link WebView#setWebChromeClient(WebChromeClient)} 才会被系统调用。</p>
 */
public class CustomWebChromeClient extends WebChromeClient {

    private static final String TAG = "[CustomWebChromeClient]";

    /** 文件选择请求码，供 Activity.onActivityResult 识别 */
    public static final int FILE_CHOOSER_REQUEST_CODE = 1002;

    private final Activity mActivity;
    private final WebView mWebView;
    private final FrameLayout mVideoContainer;

    private View mCustomView;
    private CustomViewCallback mCustomViewCallback;

    /** <input type="file"> 的回调，选择结果通过 handleFileChooserResult 回传 */
    private ValueCallback<Uri[]> mFilePathCallback;

    public CustomWebChromeClient(Activity activity, WebView webView, FrameLayout videoContainer) {
        mActivity = activity;
        mWebView = webView;
        mVideoContainer = videoContainer;
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        if (mCustomView != null) {
            callback.onCustomViewHidden();
            return;
        }

        if (mVideoContainer == null) {
            HLogger.w(TAG + " onShowCustomView ignored: videoContainer is null");
            callback.onCustomViewHidden();
            return;
        }

        mCustomView = view;
        mCustomViewCallback = callback;

        // 普通浏览视图临时隐藏，避免与全屏视图叠加渲染
        mWebView.setVisibility(View.GONE);
        mVideoContainer.addView(view);
        mVideoContainer.setVisibility(View.VISIBLE);

        setSystemUiVisibility(true);

        HLogger.d(TAG + " Entered fullscreen mode");
    }

    @Override
    public void onHideCustomView() {
        if (mCustomView == null) {
            return;
        }

        if (mVideoContainer != null) {
            mVideoContainer.removeView(mCustomView);
            mVideoContainer.setVisibility(View.GONE);
        }
        mCustomView = null;
        if (mCustomViewCallback != null) {
            mCustomViewCallback.onCustomViewHidden();
            mCustomViewCallback = null;
        }

        mWebView.setVisibility(View.VISIBLE);
        setSystemUiVisibility(false);

        HLogger.d(TAG + " Exited fullscreen mode");
    }

    /**
     * 切换沉浸式全屏 / 还原系统 UI
     *
     * <p>API 30+ 使用 {@link android.view.WindowInsetsController} 替代废弃的
     * {@code SYSTEM_UI_FLAG_*} 系列。低版本（API 21-29）回退到旧 API。</p>
     */
    private void setSystemUiVisibility(boolean fullscreen) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.view.WindowInsetsController controller =
                    mActivity.getWindow().getInsetsController();
            if (controller == null) return;
            int types = android.view.WindowInsets.Type.statusBars()
                    | android.view.WindowInsets.Type.navigationBars();
            if (fullscreen) {
                controller.hide(types);
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                controller.show(types);
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
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

    /**
     * 当前是否处于 HTML5 视频全屏状态
     *
     * <p>供 Activity 在 onBackPressed 中优先退出全屏，避免全屏后用户被锁死无法退出。</p>
     */
    public boolean isInFullscreen() {
        return mCustomView != null;
    }

    /**
     * 若当前处于全屏则退出，返回 true；否则返回 false 由调用方继续后续返回逻辑
     */
    public boolean exitFullscreenIfActive() {
        if (mCustomView == null) {
            return false;
        }
        onHideCustomView();
        return true;
    }

    // ==================== 文件选择（<input type="file">） ====================

    /**
     * 网页中点击 {@code <input type="file">} 时触发
     *
     * <p>PC 浏览器原生支持文件选择对话框；WebView 默认无响应，必须由
     * {@link WebChromeClient} 显式启动系统文件选择器并回传结果。</p>
     */
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                     FileChooserParams fileChooserParams) {
        if (mFilePathCallback != null) {
            mFilePathCallback.onReceiveValue(null);
        }
        mFilePathCallback = filePathCallback;

        try {
            Intent intent = fileChooserParams.createIntent();
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            mActivity.startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
            return true;
        } catch (Exception e) {
            mFilePathCallback = null;
            HLogger.e(TAG + " onShowFileChooser failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Activity.onActivityResult 收到文件选择结果后调用此方法回传给 WebView
     */
    public void handleFileChooserResult(int resultCode, Intent data) {
        if (mFilePathCallback == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
            ClipData clipData = data.getClipData();
            if (clipData != null) {
                results = new Uri[clipData.getItemCount()];
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    results[i] = clipData.getItemAt(i).getUri();
                }
            } else if (data.getDataString() != null) {
                results = new Uri[]{Uri.parse(data.getDataString())};
            }
        }
        mFilePathCallback.onReceiveValue(results);
        mFilePathCallback = null;
    }

    // ==================== 新窗口（window.open / target="_blank"） ====================

    /**
     * 网页调用 {@code window.open()} 或点击 {@code target="_blank"} 链接时触发
     *
     * <p>PC 浏览器会打开新标签页；WebView 默认静默丢弃。
     * 这里创建临时 WebView 接住新窗口的 URL，再重定向到主 WebView 加载，
     * 实现"在当前页打开"的单窗口行为，适合 kiosk/启动器场景。</p>
     */
    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        WebView newWebView = new WebView(view.getContext());
        newWebView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                mWebView.loadUrl(url);
                return true;
            }
        });
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(newWebView);
        resultMsg.sendToTarget();
        HLogger.d(TAG + " onCreateWindow: redirected new window URL to main WebView");
        return true;
    }

    // ==================== Web API 权限请求 ====================

    /**
     * 网页请求摄像头/麦克风等 Web API 权限时触发
     *
     * <p>PC 浏览器会弹权限对话框；WebView 默认拒绝。
     * 这里直接授予网页请求的资源（等价于用户点"允许"），保证与 PC 浏览器行为一致。
     * 注意：Android 系统级权限（CAMERA/RECORD_AUDIO）仍需在 manifest 声明并由用户授权。</p>
     */
    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        HLogger.d(TAG + " onPermissionRequest: " + Arrays.toString(request.getResources()));
        request.grant(request.getResources());
    }

    /**
     * 网页请求地理位置权限时触发
     *
     * <p>PC 浏览器弹权限对话框；WebView 默认无响应导致 geolocation API 卡死。
     * 这里直接授予，不记住（每次都重新询问）。</p>
     */
    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        HLogger.d(TAG + " onGeolocationPermissionsShowPrompt: " + origin);
        callback.invoke(origin, true, false);
    }

    // ==================== 调试日志 ====================

    /**
     * 将网页 console.log/warn/error 转发到 HLogger，便于排查页面侧问题
     */
    @Override
    public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
        String msg = "[console] " + consoleMessage.messageLevel() + ": "
                + consoleMessage.message() + " (" + consoleMessage.sourceId()
                + ":" + consoleMessage.lineNumber() + ")";
        switch (consoleMessage.messageLevel()) {
            case ERROR:
                HLogger.e(TAG + " " + msg);
                break;
            case WARNING:
                HLogger.w(TAG + " " + msg);
                break;
            default:
                HLogger.d(TAG + " " + msg);
                break;
        }
        return true;
    }
}
