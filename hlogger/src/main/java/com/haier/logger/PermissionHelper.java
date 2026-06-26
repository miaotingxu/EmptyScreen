package com.haier.logger;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Utility class for handling storage permissions
 * Android 10+ uses scoped storage, so permissions are only needed for Android 9 and below
 */
public class PermissionHelper {

    private static final int STORAGE_PERMISSION_REQUEST_CODE = 10001;

    /**
     * Check if storage permission is granted
     * For Android 10+ (API 29+), always returns true as we use scoped storage
     */
    public static boolean hasStoragePermission(Context context) {
        // Android 10+ uses scoped storage, no permission needed for app-specific directory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        
        // For Android 9 and below, check READ_EXTERNAL_STORAGE permission
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request storage permission from the user
     * Only needed for Android 9 and below
     * 
     * @param activity The activity to request permission from
     * @return true if permission was already granted or not needed, false if permission was requested
     */
    public static boolean requestStoragePermission(Activity activity) {
        // Android 10+ doesn't need this permission for app-specific directories
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }

        // Check if permission is already granted
        if (hasStoragePermission(activity)) {
            return true;
        }

        // Request permission
        activity.requestPermissions(
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                STORAGE_PERMISSION_REQUEST_CODE
        );
        
        return false;
    }

    /**
     * Handle the result of permission request
     * Call this from Activity.onRequestPermissionsResult()
     * 
     * @param requestCode The request code passed to onRequestPermissionsResult
     * @param grantResults The results of the permission request
     * @return true if permission was granted, false otherwise
     */
    public static boolean handlePermissionResult(int requestCode, int[] grantResults) {
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                return true;
            } else {
                // Permission denied
                return false;
            }
        }
        return false;
    }

    /**
     * Check if we should show permission rationale
     * 
     * @param activity The activity to check
     * @return true if we should show rationale
     */
    public static boolean shouldShowRationale(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return false;
        }
        
        return activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE);
    }
}
