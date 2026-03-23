package com.haier.emptyscreen.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Process;

import java.text.DecimalFormat;

/**
 * 内存工具类 - 提供系统内存和应用内存使用情况的查询方法
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>获取系统总内存</li>
 *   <li>获取可用内存</li>
 *   <li>获取应用内存使用率</li>
 *   <li>获取系统内存使用率</li>
 *   <li>格式化内存大小显示</li>
 * </ul>
 * 
 * @author EmptyScreen Team
 * @version 1.0
 */
public class MemoryUtils {
    
    /** 内存百分比格式化工具（保留一位小数） */
    private static final DecimalFormat sFormat = new DecimalFormat("#.#");
    
    /**
     * 获取应用内存使用百分比
     * 
     * @param context 上下文
     * @return 内存使用百分比字符串，如 "45.3%"；失败返回 "N/A"
     */
    public static String getMemoryUsagePercent(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return "N/A";
        }
        
        long totalMemory = getTotalMemory(context);
        long usedMemory = getUsedMemory(context);
        
        if (totalMemory == 0) {
            return "N/A";
        }
        
        float percent = ((float) usedMemory / totalMemory) * 100;
        return sFormat.format(percent) + "%";
    }
    
    /**
     * 获取当前应用已使用的内存大小
     * 
     * @param context 上下文
     * @return 已使用的内存字节数
     */
    public static long getUsedMemory(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return 0;
        }
        
        int pid = Process.myPid();
        int[] pids = new int[]{pid};
        Debug.MemoryInfo[] memoryInfos = activityManager.getProcessMemoryInfo(pids);
        
        if (memoryInfos != null && memoryInfos.length > 0) {
            Debug.MemoryInfo memoryInfo = memoryInfos[0];
            return memoryInfo.getTotalPss() * 1024L; // 转换为字节
        }
        
        return 0;
    }
    
    /**
     * 获取系统总内存大小
     * 
     * @param context 上下文
     * @return 总内存字节数
     */
    public static long getTotalMemory(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return 0;
        }
        
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo.totalMem;
    }
    
    /**
     * 获取系统可用内存大小
     * 
     * @param context 上下文
     * @return 可用内存字节数
     */
    public static long getAvailableMemory(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return 0;
        }
        
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo.availMem;
    }
    
    /**
     * 获取系统内存使用百分比
     * 
     * @param context 上下文
     * @return 内存使用百分比（0-100），失败返回 0
     */
    public static float getSystemMemoryUsagePercent(Context context) {
        long total = getTotalMemory(context);
        long available = getAvailableMemory(context);
        if (total == 0) return 0;
        
        return ((float) (total - available) / total) * 100;
    }
    
    /**
     * 格式化内存大小为可读字符串
     * 
     * @param bytes 字节数
     * @return 格式化后的字符串，如 "1.5 MB"
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return sFormat.format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return sFormat.format(bytes / (1024.0 * 1024)) + " MB";
        } else {
            return sFormat.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
        }
    }
    
    /**
     * 打印完整的内存信息到日志
     * 
     * @param context 上下文
     */
    public static void logMemoryInfo(Context context) {
        LogUtils.d("[MemoryUtils] App Memory Usage: " + getMemoryUsagePercent(context));
        LogUtils.d("[MemoryUtils] App Used Memory: " + formatSize(getUsedMemory(context)));
        LogUtils.d("[MemoryUtils] Total Memory: " + formatSize(getTotalMemory(context)));
        LogUtils.d("[MemoryUtils] Available Memory: " + formatSize(getAvailableMemory(context)));
        LogUtils.d("[MemoryUtils] System Memory Usage: " + String.format("%.1f%%", getSystemMemoryUsagePercent(context)));
    }
}
