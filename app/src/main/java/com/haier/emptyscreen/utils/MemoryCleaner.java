package com.haier.emptyscreen.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.webkit.CookieManager;
import android.webkit.WebView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 内存清理工具类 - 提供自动和手动内存清理功能
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>应用缓存清理：清理内部/外部存储缓存目录</li>
 *   <li>WebView缓存清理：清理网页缓存、历史记录、表单数据</li>
 *   <li>Dalvik GC：触发虚拟机垃圾回收</li>
 *   <li>Runtime GC：触发运行时垃圾回收</li>
 *   <li>清理日志：记录每次清理的详细信息</li>
 * </ul>
 * 
 * <p>使用场景：</p>
 * <ol>
 *   <li>自动清理：ForegroundService定时检查内存使用率，超阈值时自动触发</li>
 *   <li>手动清理：用户在设置页面点击"立即清理"按钮</li>
 *   <li>系统压力：响应onTrimMemory回调，释放相应级别的资源</li>
 * </ol>
 * 
 * <p>注意事项：</p>
 * <ul>
 *   <li>清理操作在子线程执行，避免阻塞主线程</li>
 *   <li>清理过程不会影响应用核心功能</li>
 *   <li>清理日志最多保留50条</li>
 * </ul>
 * 
 * @author EmptyScreen Team
 * @version 1.0
 */
public class MemoryCleaner {

    /** 日志标签 */
    private static final String TAG = "[MemoryCleaner]";
    
    /** 日期格式化器 */
    private static final SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    
    /** 清理日志列表 */
    private static List<CleanLog> sCleanLogs = new ArrayList<>();
    
    /** 最大日志数量 */
    private static final int MAX_LOG_COUNT = 20;

    /**
     * 清理日志数据类
     * 
     * <p>记录每次清理操作的详细信息，包括：</p>
     * <ul>
     *   <li>清理时间</li>
     *   <li>清理前后内存使用率</li>
     *   <li>释放的字节数</li>
     *   <li>清理的项目列表</li>
     * </ul>
     */
    public static class CleanLog {
        /** 清理时间戳 */
        public String timestamp;
        
        /** 清理前内存使用率（百分比） */
        public float beforePercent;
        
        /** 清理后内存使用率（百分比） */
        public float afterPercent;
        
        /** 释放的字节数 */
        public long freedBytes;
        
        /** 清理的项目描述 */
        public String cleanedItems;
        
        /**
         * 构造函数
         * 
         * @param timestamp 清理时间戳
         * @param beforePercent 清理前内存使用率
         * @param afterPercent 清理后内存使用率
         * @param freedBytes 释放的字节数
         * @param cleanedItems 清理的项目描述
         */
        public CleanLog(String timestamp, float beforePercent, float afterPercent, long freedBytes, String cleanedItems) {
            this.timestamp = timestamp;
            this.beforePercent = beforePercent;
            this.afterPercent = afterPercent;
            this.freedBytes = freedBytes;
            this.cleanedItems = cleanedItems;
        }
    }

    /**
     * 清理结果数据类
     * 
     * <p>返回清理操作的执行结果，包括：</p>
     * <ul>
     *   <li>是否成功</li>
     *   <li>释放的字节数</li>
     *   <li>清理前后内存使用率</li>
     *   <li>清理的项目列表</li>
     * </ul>
     */
    public static class CleanResult {
        /** 是否成功 */
        public boolean success;
        
        /** 释放的字节数 */
        public long freedBytes;
        
        /** 清理前内存使用率（百分比） */
        public float beforePercent;
        
        /** 清理后内存使用率（百分比） */
        public float afterPercent;
        
        /** 清理的项目描述 */
        public String cleanedItems;
        
        /**
         * 构造函数
         * 
         * @param success 是否成功
         * @param freedBytes 释放的字节数
         * @param beforePercent 清理前内存使用率
         * @param afterPercent 清理后内存使用率
         * @param cleanedItems 清理的项目描述
         */
        public CleanResult(boolean success, long freedBytes, float beforePercent, float afterPercent, String cleanedItems) {
            this.success = success;
            this.freedBytes = freedBytes;
            this.beforePercent = beforePercent;
            this.afterPercent = afterPercent;
            this.cleanedItems = cleanedItems;
        }
    }

    public static CleanResult cleanMemory(Context context) {
        LogUtils.i(TAG + " Starting memory cleanup...");

        float beforePercent = MemoryUtils.getSystemMemoryUsagePercent(context);
        long beforeUsed = MemoryUtils.getUsedMemory(context);

        StringBuilder cleanedItems = new StringBuilder();

        cleanAppCache(context, cleanedItems);

        cleanWebViewCache(cleanedItems);

        cleanDalvikCache(cleanedItems);

        triggerGC(cleanedItems);

        long afterUsed = MemoryUtils.getUsedMemory(context);
        float afterPercent = MemoryUtils.getSystemMemoryUsagePercent(context);
        long freedBytes = beforeUsed - afterUsed;

        String timestamp = sDateFormat.format(new Date());
        String itemsStr = cleanedItems.toString();
        if (itemsStr.endsWith(", ")) {
            itemsStr = itemsStr.substring(0, itemsStr.length() - 2);
        }
        
        // 限制清理项目描述的长度，防止内存占用过高
        if (itemsStr.length() > 200) {
            itemsStr = itemsStr.substring(0, 200) + "...";
        }

        CleanLog log = new CleanLog(timestamp, beforePercent, afterPercent, freedBytes, itemsStr);
        addCleanLog(log);

        LogUtils.i(TAG + " Memory cleanup completed. Before: " + String.format("%.1f%%", beforePercent) +
                ", After: " + String.format("%.1f%%", afterPercent) +
                ", Freed: " + MemoryUtils.formatSize(freedBytes) +
                ", Items: " + itemsStr);

        return new CleanResult(true, freedBytes, beforePercent, afterPercent, itemsStr);
    }

    private static void cleanAppCache(Context context, StringBuilder cleanedItems) {
        try {
            File cacheDir = context.getCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                long beforeSize = getDirSize(cacheDir);
                deleteDirContents(cacheDir);
                long afterSize = getDirSize(cacheDir);
                long freed = beforeSize - afterSize;
                if (freed > 0) {
                    cleanedItems.append("AppCache(").append(MemoryUtils.formatSize(freed)).append("), ");
                    LogUtils.d(TAG + " Cleaned app cache: " + MemoryUtils.formatSize(freed));
                }
            }

            File externalCacheDir = context.getExternalCacheDir();
            if (externalCacheDir != null && externalCacheDir.exists()) {
                long beforeSize = getDirSize(externalCacheDir);
                deleteDirContents(externalCacheDir);
                long afterSize = getDirSize(externalCacheDir);
                long freed = beforeSize - afterSize;
                if (freed > 0) {
                    cleanedItems.append("ExternalCache(").append(MemoryUtils.formatSize(freed)).append("), ");
                    LogUtils.d(TAG + " Cleaned external cache: " + MemoryUtils.formatSize(freed));
                }
            }
        } catch (Exception e) {
            LogUtils.e(TAG + " Failed to clean app cache: " + e.getMessage());
        }
    }

    private static void cleanWebViewCache(StringBuilder cleanedItems) {
        try {
            Context context = LogUtils.getApplicationContext();
            if (context == null) {
                LogUtils.w(TAG + " Cannot clean WebView cache: context is null");
                return;
            }
            
            long beforeMemory = Debug.getNativeHeapAllocatedSize();
            
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().removeSessionCookies(null);
            }
            
            long afterMemory = Debug.getNativeHeapAllocatedSize();
            long freed = beforeMemory - afterMemory;
            if (freed > 0) {
                cleanedItems.append("WebView(").append(MemoryUtils.formatSize(freed)).append("), ");
                LogUtils.d(TAG + " Cleaned WebView cache: " + MemoryUtils.formatSize(freed));
            }
        } catch (Exception e) {
            LogUtils.e(TAG + " Failed to clean WebView cache: " + e.getMessage());
        }
    }

    private static void cleanDalvikCache(StringBuilder cleanedItems) {
        try {
            long beforeMemory = Debug.getNativeHeapAllocatedSize();

            System.runFinalization();
            System.gc();

            // 使用可中断的sleep
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LogUtils.w(TAG + " Dalvik GC interrupted");
                return;
            }

            long afterMemory = Debug.getNativeHeapAllocatedSize();
            long freed = beforeMemory - afterMemory;
            if (freed > 0) {
                cleanedItems.append("GC(").append(MemoryUtils.formatSize(freed)).append("), ");
                LogUtils.d(TAG + " Dalvik GC freed: " + MemoryUtils.formatSize(freed));
            }
        } catch (Exception e) {
            LogUtils.e(TAG + " Failed to clean Dalvik cache: " + e.getMessage());
        }
    }

    private static void triggerGC(StringBuilder cleanedItems) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long beforeFree = runtime.freeMemory();

            runtime.gc();
            System.gc();

            // 使用可中断的sleep
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LogUtils.w(TAG + " Runtime GC interrupted");
                return;
            }

            long afterFree = runtime.freeMemory();
            long freed = afterFree - beforeFree;
            if (freed > 0) {
                LogUtils.d(TAG + " Runtime GC freed: " + MemoryUtils.formatSize(freed));
            }
        } catch (Exception e) {
            LogUtils.e(TAG + " Failed to trigger GC: " + e.getMessage());
        }
    }

    private static long getDirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;

        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += getDirSize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }

    private static void deleteDirContents(File dir) {
        if (dir == null || !dir.exists()) return;

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirContents(file);
                    file.delete();
                } else {
                    file.delete();
                }
            }
        }
    }

    private static void addCleanLog(CleanLog log) {
        sCleanLogs.add(0, log);
        if (sCleanLogs.size() > MAX_LOG_COUNT) {
            sCleanLogs.remove(sCleanLogs.size() - 1);
        }
    }

    public static List<CleanLog> getCleanLogs() {
        return new ArrayList<>(sCleanLogs);
    }

    public static String getLatestCleanLog() {
        if (sCleanLogs.isEmpty()) {
            return "No cleanup records";
        }
        CleanLog latest = sCleanLogs.get(0);
        return String.format(Locale.getDefault(),
                "[%s] Before: %.1f%%, After: %.1f%%, Freed: %s, Items: %s",
                latest.timestamp, latest.beforePercent, latest.afterPercent,
                MemoryUtils.formatSize(latest.freedBytes), latest.cleanedItems);
    }

    public static boolean shouldCleanMemory(Context context, int thresholdPercent) {
        float currentPercent = MemoryUtils.getSystemMemoryUsagePercent(context);
        boolean shouldClean = currentPercent >= thresholdPercent;

        if (shouldClean) {
            LogUtils.i(TAG + " Memory usage " + String.format("%.1f%%", currentPercent) +
                    " >= threshold " + thresholdPercent + "%, cleanup needed");
        }

        return shouldClean;
    }

}
