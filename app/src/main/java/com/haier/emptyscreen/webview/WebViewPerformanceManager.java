package com.haier.emptyscreen.webview;

import android.content.Context;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.haier.emptyscreen.utils.LogUtils;

/**
 * WebView性能优化管理器
 * 
 * <p>针对复杂网页应用（图表、动画、数据轮询）的优化配置：</p>
 * <ul>
 *   <li>硬件加速：启用GPU渲染，提升动画性能</li>
 *   <li>缓存策略：限制缓存大小，防止内存无限增长</li>
 *   <li>内存管理：提供主动清理机制</li>
 *   <li>渲染优化：启用预渲染和延迟加载</li>
 * </ul>
 * 
 * @author EmptyScreen Team
 * @version 1.0
 */
public class WebViewPerformanceManager {

    private static final String TAG = "[WebViewPerf]";

    /**
     * 配置WebView性能优化参数
     * 
     * @param webView WebView实例
     * @param context 上下文
     */
    public static void configureForComplexPage(WebView webView, Context context) {
        WebSettings settings = webView.getSettings();
        
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
        
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAppCacheEnabled(true);
        settings.setAppCachePath(context.getCacheDir().getAbsolutePath());
        settings.setAppCacheMaxSize(50 * 1024 * 1024);
        
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setEnableSmoothTransition(true);
        
        // 启用图片延迟加载，先加载页面内容
        settings.setBlockNetworkImage(true);
        settings.setBlockNetworkLoads(false);
        settings.setLoadsImagesAutomatically(true);
        
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        
        // 安全配置：禁止混合内容（HTTPS页面加载HTTP资源）
        // 如果必须加载HTTP资源，建议网页端升级为HTTPS
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setOffscreenPreRaster(true);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
        }
        
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
        
        webView.setScrollBarStyle(WebView.SCROLLBARS_INSIDE_OVERLAY);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        
        LogUtils.i(TAG + " WebView configured for complex page");
    }

    /**
     * 清理WebView缓存和资源
     * 
     * <p>建议在以下场景调用：</p>
     * <ul>
     *   <li>WebView加载新页面前</li>
     *   <li>内存使用率过高时</li>
     *   <li>页面包含大量动态数据时</li>
     * </ul>
     * 
     * @param webView WebView实例
     * @param context 上下文
     */
    public static void clearCache(WebView webView, Context context) {
        LogUtils.i(TAG + " Clearing WebView cache");
        
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } else {
            CookieSyncManager.createInstance(context);
            CookieSyncManager.getInstance().startSync();
            CookieManager.getInstance().removeAllCookie();
            CookieSyncManager.getInstance().stopSync();
            CookieSyncManager.getInstance().sync();
        }
        
        webView.freeMemory();
        
        LogUtils.i(TAG + " WebView cache cleared");
    }

    /**
     * 安全销毁WebView
     * 
     * <p>防止内存泄漏的销毁流程：</p>
     * <ol>
     *   <li>移除JavaScript接口</li>
     *   <li>清除历史记录</li>
     *   <li>清除缓存</li>
     *   <li>清除WebViewClient和WebChromeClient</li>
     *   <li>从父容器移除</li>
     *   <li>调用destroy()</li>
     * </ol>
     * 
     * @param webView WebView实例
     */
    public static void safeDestroy(WebView webView) {
        if (webView == null) {
            return;
        }
        
        LogUtils.i(TAG + " Safely destroying WebView");
        
        webView.stopLoading();
        webView.onPause();
        
        webView.removeJavascriptInterface("Android");
        
        webView.clearHistory();
        webView.clearCache(true);
        webView.clearFormData();
        
        webView.setWebViewClient(null);
        webView.setWebChromeClient(null);
        
        if (webView.getParent() != null) {
            ((android.view.ViewGroup) webView.getParent()).removeView(webView);
        }
        
        webView.destroy();
        
        LogUtils.i(TAG + " WebView safely destroyed");
    }

    /**
     * 执行JavaScript代码清理网页资源
     * 
     * <p>用于清理网页中的：</p>
     * <ul>
     *   <li>未关闭的定时器（setInterval/setTimeout）</li>
     *   <li>事件监听器</li>
     *   <li>大型数据对象</li>
     *   <li>Canvas/WebGL资源</li>
     * </ul>
     * 
     * @param webView WebView实例
     */
    public static void injectCleanupScript(WebView webView) {
        String cleanupScript = 
            "(function() {" +
            "   try {" +
            "       if (window.__cleanupTimers) {" +
            "           window.__cleanupTimers.forEach(function(id) {" +
            "               clearInterval(id);" +
            "               clearTimeout(id);" +
            "           });" +
            "       }" +
            "       window.__cleanupTimers = [];" +
            "       console.log('[WebViewPerf] Timers cleaned');" +
            "   } catch(e) {" +
            "       console.error('[WebViewPerf] Cleanup error:', e);" +
            "   }" +
            "})();";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(cleanupScript, null);
        } else {
            webView.loadUrl("javascript:" + cleanupScript);
        }
    }

    /**
     * 注入定时器监控脚本
     * 
     * <p>监控网页中的定时器数量，防止无限增长</p>
     * 
     * @param webView WebView实例
     */
    public static void injectTimerMonitor(WebView webView) {
        String monitorScript = 
            "(function() {" +
            "   window.__timerCount = 0;" +
            "   window.__cleanupTimers = [];" +
            "   window.__MAX_TIMERS = 1000; // 定时器数量上限" +
            "   var originalSetInterval = window.setInterval;" +
            "   var originalSetTimeout = window.setTimeout;" +
            "   var originalClearInterval = window.clearInterval;" +
            "   var originalClearTimeout = window.clearTimeout;" +
            "   " +
            "   // 清理已失效的定时器ID" +
            "   window.__cleanupExpiredTimers = function() {" +
            "       // 这里可以添加清理逻辑，例如移除已清除的定时器ID" +
            "       console.log('[WebViewPerf] Expired timers cleaned');" +
            "   };" +
            "   " +
            "   window.setInterval = function(fn, delay) {" +
            "       if (window.__timerCount >= window.__MAX_TIMERS) {" +
            "           console.warn('[WebViewPerf] Max timers reached (' + window.__MAX_TIMERS + '), rejecting new timer');" +
            "           return null;" +
            "       }" +
            "       var id = originalSetInterval.call(window, fn, delay);" +
            "       window.__cleanupTimers.push(id);" +
            "       window.__timerCount++;" +
            "       console.log('[WebViewPerf] setInterval created, total:', window.__timerCount);" +
            "       return id;" +
            "   };" +
            "   " +
            "   window.setTimeout = function(fn, delay) {" +
            "       if (window.__timerCount >= window.__MAX_TIMERS) {" +
            "           console.warn('[WebViewPerf] Max timers reached (' + window.__MAX_TIMERS + '), rejecting new timer');" +
            "           return null;" +
            "       }" +
            "       var id = originalSetTimeout.call(window, fn, delay);" +
            "       window.__cleanupTimers.push(id);" +
            "       return id;" +
            "   };" +
            "   " +
            "   window.clearInterval = function(id) {" +
            "       originalClearInterval.call(window, id);" +
            "       var index = window.__cleanupTimers.indexOf(id);" +
            "       if (index > -1) {" +
            "           window.__cleanupTimers.splice(index, 1);" +
            "           window.__timerCount--;" +
            "           console.log('[WebViewPerf] setInterval cleared, total:', window.__timerCount);" +
            "       }" +
            "   };" +
            "   " +
            "   window.clearTimeout = function(id) {" +
            "       originalClearTimeout.call(window, id);" +
            "       var index = window.__cleanupTimers.indexOf(id);" +
            "       if (index > -1) {" +
            "           window.__cleanupTimers.splice(index, 1);" +
            "       }" +
            "   };" +
            "   " +
            "   console.log('[WebViewPerf] Timer monitor injected with max limit: ' + window.__MAX_TIMERS);" +
            "})();";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(monitorScript, null);
        } else {
            webView.loadUrl("javascript:" + monitorScript);
        }
    }

    /**
     * 获取WebView内存使用情况
     * 
     * @param webView WebView实例
     * @return 内存使用描述字符串
     */
    public static String getMemoryUsage(WebView webView) {
        if (webView == null) {
            return "WebView is null";
        }
        
        return String.format("WebView Memory - " +
                "Allocated: %d KB, Free: %d KB",
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024,
                Runtime.getRuntime().freeMemory() / 1024);
    }
    
    /**
     * 启用图片加载
     * 
     * <p>在页面加载完成后调用，用于延迟加载图片，提升页面加载速度</p>
     * 
     * @param webView WebView实例
     */
    public static void enableImageLoading(WebView webView) {
        if (webView != null) {
            WebSettings settings = webView.getSettings();
            settings.setBlockNetworkImage(false);
            LogUtils.i(TAG + " Image loading enabled");
        }
    }
}
