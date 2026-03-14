package com.haier.emptyscreen.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.view.Surface;
import android.view.WindowManager;

public class OrientationHelper {
    
    public static final int ORIENTATION_PORTRAIT = 0;
    public static final int ORIENTATION_LANDSCAPE = 1;
    
    public static int getCurrentOrientation(Context context) {
        int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();
        
        int orientation = context.getResources().getConfiguration().orientation;
        
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return ORIENTATION_PORTRAIT;
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return ORIENTATION_LANDSCAPE;
        }
        
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            return ORIENTATION_PORTRAIT;
        } else {
            return ORIENTATION_LANDSCAPE;
        }
    }
    
    public static boolean isPortrait(Context context) {
        return getCurrentOrientation(context) == ORIENTATION_PORTRAIT;
    }
    
    public static boolean isLandscape(Context context) {
        return getCurrentOrientation(context) == ORIENTATION_LANDSCAPE;
    }
    
    public static void setAutoOrientation(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }
    
    public static void lockToPortrait(Activity activity) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    
    public static void lockToLandscape(Activity activity) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
    
    public static void lockToCurrentOrientation(Activity activity) {
        int currentOrientation = getCurrentOrientation(activity);
        if (currentOrientation == ORIENTATION_PORTRAIT) {
            lockToPortrait(activity);
        } else {
            lockToLandscape(activity);
        }
    }
    
    public static int getScreenOrientationType(Context context) {
        return context.getResources().getConfiguration().orientation;
    }
    
    public static String getOrientationName(Context context) {
        int orientation = getCurrentOrientation(context);
        return orientation == ORIENTATION_PORTRAIT ? "Portrait" : "Landscape";
    }
}
