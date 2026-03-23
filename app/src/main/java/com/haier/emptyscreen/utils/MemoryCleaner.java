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
 */
public class MemoryCleaner {

    private static final String TAG = "[MemoryCleaner]";
    
    private static final SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    
    private static List<CleanLog> sCleanLogs = new ArrayList<>();
    
    private static final int MAX_LOG_COUNT = 20;

    /**
     * 清理日志数据类
     */
    public static class CleanLog {
        public String timestamp;
        public float beforePercent;
        public float afterPercent;
        public long freedBytes;
        public String cleanedItems;
        
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
     */
    public static class CleanResult {
        public boolean success;
        public long freedBytes;
        public float beforePercent;
        public float afterPercent;
        public String cleanedItems;
        
        public CleanResult(boolean success, long freedBytes, float beforePercent, float afterPercent, String cleanedItems) {
            this.success = success;
            this.freedBytes = freedBytes;
            this.beforePercent = beforePercent;
            this.afterPercent = afterPercent;
            this.cleanedItems = cleanedItems;
        }
    }

    /**
     * 执行内存清理
     * 
     * @param context 上下文
     * @return 清理结果
     */
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

    /**
     * 清理应用缓存
     */
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

    /**
     * 清理 WebView 缓存
     */
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

    /**
     * 清理 Dalvik 缓存
     */
    private static void cleanDalvikCache(StringBuilder cleanedItems) {
        try {
            long beforeMemory = Debug.getNativeHeapAllocatedSize();

            System.runFinalization();
            System.gc();

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

    /**
     * 触发垃圾回收
     */
    private static void triggerGC(StringBuilder cleanedItems) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long beforeFree = runtime.freeMemory();

            runtime.gc();
            System.gc();

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

    /**
     * 获取目录大小
     */
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

    /**
     * 删除目录内容
     */
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

    /**
     * 添加清理日志
     */
    private static void addCleanLog(CleanLog log) {
        sCleanLogs.add(0, log);
        if (sCleanLogs.size() > MAX_LOG_COUNT) {
            sCleanLogs.remove(sCleanLogs.size() - 1);
        }
    }

    /**
     * 获取所有清理日志
     */
    public static List<CleanLog> getCleanLogs() {
        return new ArrayList<>(sCleanLogs);
    }

    /**
     * 获取最新清理日志
     */
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

    /**
     * 判断是否需要清理内存
     * 
     * @param context 上下文
     * @param thresholdPercent 阈值百分比
     * @return true-需要清理；false-不需要
     */
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
