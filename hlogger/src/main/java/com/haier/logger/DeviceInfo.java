package com.haier.logger;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DeviceInfo {

    public static String collect(Context context) {
        StringBuilder sb = new StringBuilder();

        sb.append("App: ").append(getAppInfo(context)).append("\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("OS: Android ").append(Build.VERSION.RELEASE)
          .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("RAM: ").append(getMemoryInfo(context)).append("\n");
        sb.append("Storage: ").append(getStorageInfo(context)).append("\n");
        sb.append("Screen: ").append(getScreenInfo(context)).append("\n");
        sb.append("Locale: ").append(Locale.getDefault().toString()).append("\n");
        sb.append("Timezone: ").append(java.util.TimeZone.getDefault().getID()).append("\n");

        String clientId = TokenManager.getDeviceNo();
        if (clientId != null) {
            sb.append("Client ID: ").append(clientId).append("\n");
        }

        sb.append("Collect Time: ").append(
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append("\n");

        return sb.toString();
    }

    private static String getAppInfo(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return context.getPackageName() + " v" + pi.versionName + " (build " + pi.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return context.getPackageName();
        }
    }

    private static String getMemoryInfo(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long totalMB = mi.totalMem / (1024 * 1024);
        long availMB = mi.availMem / (1024 * 1024);
        return totalMB + "MB total, " + availMB + "MB available";
    }

    private static String getStorageInfo(Context context) {
        // Check storage permission for Android 10+
        if (!hasStoragePermission(context)) {
            return "Permission denied (storage access requires READ_EXTERNAL_STORAGE)";
        }
        
        try {
            // For Android 10+, use getExternalFilesDir() which doesn't require permission
            // For older versions, use Environment.getDataDirectory()
            StatFs stat;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - use app-specific directory (no permission needed)
                if (context.getExternalFilesDir(null) != null) {
                    stat = new StatFs(context.getExternalFilesDir(null).getPath());
                } else {
                    stat = new StatFs(Environment.getDataDirectory().getPath());
                }
            } else {
                // Android 9 and below
                stat = new StatFs(Environment.getDataDirectory().getPath());
            }
            
            long totalBytes = stat.getTotalBytes();
            long availBytes = stat.getAvailableBytes();
            long totalGB = totalBytes / (1024 * 1024 * 1024);
            long availGB = availBytes / (1024 * 1024 * 1024);
            return totalGB + "GB total, " + availGB + "GB available";
        } catch (Exception e) {
            return "unknown (" + e.getMessage() + ")";
        }
    }

    /**
     * Check if the app has storage permission
     * For Android 10+ (API 29+), external storage access uses scoped storage
     */
    private static boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses scoped storage, getExternalFilesDir() doesn't need permission
            // But we still check for backward compatibility
            return true;
        } else {
            // Android 9 and below need explicit permission
            try {
                PackageManager pm = context.getPackageManager();
                PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
                if (info.requestedPermissions != null) {
                    for (String perm : info.requestedPermissions) {
                        if (Manifest.permission.READ_EXTERNAL_STORAGE.equals(perm)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                // If we can't check, assume permission is granted
                return true;
            }
            return false;
        }
    }

    private static String getScreenInfo(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.widthPixels + "x" + dm.heightPixels + " @" + dm.densityDpi + "dpi";
    }
}
