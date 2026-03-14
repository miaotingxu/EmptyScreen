package com.haier.emptyscreen.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Process;

import java.text.DecimalFormat;

public class MemoryUtils {
    
    private static final DecimalFormat sFormat = new DecimalFormat("#.#");
    
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
            return memoryInfo.getTotalPss() * 1024L;
        }
        
        return 0;
    }
    
    public static long getTotalMemory(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return 0;
        }
        
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo.totalMem;
    }
    
    public static long getAvailableMemory(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return 0;
        }
        
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo.availMem;
    }
    
    public static float getSystemMemoryUsagePercent(Context context) {
        long total = getTotalMemory(context);
        long available = getAvailableMemory(context);
        if (total == 0) return 0;
        
        return ((float) (total - available) / total) * 100;
    }
    
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
    
    public static void logMemoryInfo(Context context) {
        LogUtils.d("[MemoryUtils] App Memory Usage: " + getMemoryUsagePercent(context));
        LogUtils.d("[MemoryUtils] App Used Memory: " + formatSize(getUsedMemory(context)));
        LogUtils.d("[MemoryUtils] Total Memory: " + formatSize(getTotalMemory(context)));
        LogUtils.d("[MemoryUtils] Available Memory: " + formatSize(getAvailableMemory(context)));
        LogUtils.d("[MemoryUtils] System Memory Usage: " + String.format("%.1f%%", getSystemMemoryUsagePercent(context)));
    }
}
