package com.haier.emptyscreen.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {
    private static final String PREFS_NAME = "empty_screen_prefs";
    
    private static final String KEY_URL = "url";
    private static final String KEY_BOOT_DELAY_SECONDS = "boot_delay_seconds";
    private static final String KEY_FOREGROUND_DELAY_SECONDS = "foreground_delay_seconds";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_IS_ON_SETTINGS_PAGE = "is_on_settings_page";
    
    private static final String DEFAULT_URL = "https://www.baidu.com";
    private static final int DEFAULT_BOOT_DELAY = 10;
    private static final int DEFAULT_FOREGROUND_DELAY = 30;
    
    private static PrefsManager sInstance;
    private final SharedPreferences mPrefs;
    
    private PrefsManager(Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized PrefsManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PrefsManager(context);
        }
        return sInstance;
    }
    
    public void saveUrl(String url) {
        mPrefs.edit().putString(KEY_URL, url).apply();
        LogUtils.i("[PrefsManager] URL saved: " + url);
    }
    
    public String getUrl() {
        return mPrefs.getString(KEY_URL, DEFAULT_URL);
    }
    
    public void saveBootDelaySeconds(int seconds) {
        mPrefs.edit().putInt(KEY_BOOT_DELAY_SECONDS, seconds).apply();
        LogUtils.i("[PrefsManager] Boot delay saved: " + seconds + " seconds");
    }
    
    public int getBootDelaySeconds() {
        return mPrefs.getInt(KEY_BOOT_DELAY_SECONDS, DEFAULT_BOOT_DELAY);
    }
    
    public void saveForegroundDelaySeconds(int seconds) {
        mPrefs.edit().putInt(KEY_FOREGROUND_DELAY_SECONDS, seconds).apply();
        LogUtils.i("[PrefsManager] Foreground delay saved: " + seconds + " seconds");
    }
    
    public int getForegroundDelaySeconds() {
        return mPrefs.getInt(KEY_FOREGROUND_DELAY_SECONDS, DEFAULT_FOREGROUND_DELAY);
    }
    
    public boolean isFirstLaunch() {
        return mPrefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }
    
    public void setFirstLaunchComplete() {
        mPrefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }
    
    public void setOnSettingsPage(boolean isOnSettingsPage) {
        mPrefs.edit().putBoolean(KEY_IS_ON_SETTINGS_PAGE, isOnSettingsPage).apply();
        LogUtils.i("[PrefsManager] On settings page: " + isOnSettingsPage);
    }
    
    public boolean isOnSettingsPage() {
        return mPrefs.getBoolean(KEY_IS_ON_SETTINGS_PAGE, false);
    }
    
    public void clearAll() {
        mPrefs.edit().clear().apply();
        LogUtils.i("[PrefsManager] All preferences cleared");
    }
}
