package com.haier.emptyscreen.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 偏好设置管理工具类 - 统一管理应用 SharedPreferences
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>URL 配置保存与读取</li>
 *   <li>启动延迟时间设置</li>
 *   <li>前台服务延迟设置</li>
 *   <li>内存清理相关配置</li>
 *   <li>首次启动标记</li>
 * </ul>
 * 
 * <p>设计模式：单例模式</p>
 * 
 * @author EmptyScreen Team
 * @version 1.0
 */
public class PrefsManager {
    
    /** SharedPreferences 文件名 */
    private static final String PREFS_NAME = "empty_screen_prefs";
    
    /** URL 配置键 */
    private static final String KEY_URL = "url";
    
    /** 开机启动延迟键（秒） */
    private static final String KEY_BOOT_DELAY_SECONDS = "boot_delay_seconds";
    
    /** 前台服务触发延迟键（秒） */
    private static final String KEY_FOREGROUND_DELAY_SECONDS = "foreground_delay_seconds";
    
    /** 首次启动标记键 */
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    
    /** 是否在设置页面标记键 */
    private static final String KEY_IS_ON_SETTINGS_PAGE = "is_on_settings_page";
    
    /** 内存清理阈值键（百分比） */
    private static final String KEY_MEMORY_CLEAN_THRESHOLD = "memory_clean_threshold";
    
    /** 内存清理启用状态键 */
    private static final String KEY_MEMORY_CLEAN_ENABLED = "memory_clean_enabled";
    
    /** 内存清理间隔键（秒） */
    private static final String KEY_MEMORY_CLEAN_INTERVAL = "memory_clean_interval";
    
    /** 默认 URL（内网测试地址） */
    private static final String DEFAULT_URL = "http://192.168.11.42:8096/#/?hospitalCode=4290060102";
    
    /** 默认开机延迟（10 秒） */
    private static final int DEFAULT_BOOT_DELAY = 10;
    
    /** 默认前台服务延迟（30 秒） */
    private static final int DEFAULT_FOREGROUND_DELAY = 30;
    
    /** 默认内存清理阈值（80%） */
    private static final int DEFAULT_MEMORY_CLEAN_THRESHOLD = 80;
    
    /** 默认启用内存清理 */
    private static final boolean DEFAULT_MEMORY_CLEAN_ENABLED = true;
    
    /** 默认内存清理间隔（60 秒） */
    private static final int DEFAULT_MEMORY_CLEAN_INTERVAL = 60;
    
    /** 单例实例 */
    private static PrefsManager sInstance;
    
    /** SharedPreferences 对象 */
    private final SharedPreferences mPrefs;
    
    /**
     * 私有构造函数 - 防止外部实例化
     * 
     * @param context 上下文
     */
    private PrefsManager(Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 获取单例实例
     * 
     * @param context 上下文
     * @return PrefsManager 单例
     */
    public static synchronized PrefsManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PrefsManager(context);
        }
        return sInstance;
    }
    
    /**
     * 保存 URL 配置
     * 
     * @param url 要保存的 URL
     */
    public void saveUrl(String url) {
        mPrefs.edit().putString(KEY_URL, url).apply();
        LogUtils.i("[PrefsManager] URL saved: " + url);
    }
    
    /**
     * 获取已保存的 URL
     * 
     * @return URL 字符串，未设置则返回默认值
     */
    public String getUrl() {
        return mPrefs.getString(KEY_URL, DEFAULT_URL);
    }
    
    /**
     * 保存开机启动延迟时间
     * 
     * @param seconds 延迟秒数
     */
    public void saveBootDelaySeconds(int seconds) {
        mPrefs.edit().putInt(KEY_BOOT_DELAY_SECONDS, seconds).apply();
        LogUtils.i("[PrefsManager] Boot delay saved: " + seconds + " seconds");
    }
    
    /**
     * 获取开机启动延迟时间
     * 
     * @return 延迟秒数，未设置则返回默认值
     */
    public int getBootDelaySeconds() {
        return mPrefs.getInt(KEY_BOOT_DELAY_SECONDS, DEFAULT_BOOT_DELAY);
    }
    
    /**
     * 保存前台服务触发延迟时间
     * 
     * @param seconds 延迟秒数
     */
    public void saveForegroundDelaySeconds(int seconds) {
        mPrefs.edit().putInt(KEY_FOREGROUND_DELAY_SECONDS, seconds).apply();
        LogUtils.i("[PrefsManager] Foreground delay saved: " + seconds + " seconds");
    }
    
    /**
     * 获取前台服务触发延迟时间
     * 
     * @return 延迟秒数，未设置则返回默认值
     */
    public int getForegroundDelaySeconds() {
        return mPrefs.getInt(KEY_FOREGROUND_DELAY_SECONDS, DEFAULT_FOREGROUND_DELAY);
    }
    
    /**
     * 检查是否为首次启动
     * 
     * @return true-首次启动；false-非首次
     */
    public boolean isFirstLaunch() {
        return mPrefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }
    
    /**
     * 设置首次启动已完成
     */
    public void setFirstLaunchComplete() {
        mPrefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }
    
    /**
     * 设置是否正在设置页面
     * 
     * @param isOnSettingsPage true-正在设置页面；false-不在
     */
    public void setOnSettingsPage(boolean isOnSettingsPage) {
        mPrefs.edit().putBoolean(KEY_IS_ON_SETTINGS_PAGE, isOnSettingsPage).apply();
        LogUtils.i("[PrefsManager] On settings page: " + isOnSettingsPage);
    }
    
    /**
     * 检查是否正在设置页面
     * 
     * @return true-正在设置页面；false-不在
     */
    public boolean isOnSettingsPage() {
        return mPrefs.getBoolean(KEY_IS_ON_SETTINGS_PAGE, false);
    }
    
    /**
     * 保存内存清理阈值
     * 
     * @param threshold 阈值百分比（如 80 表示 80%）
     */
    public void saveMemoryCleanThreshold(int threshold) {
        mPrefs.edit().putInt(KEY_MEMORY_CLEAN_THRESHOLD, threshold).apply();
        LogUtils.i("[PrefsManager] Memory clean threshold saved: " + threshold + "%");
    }
    
    /**
     * 获取内存清理阈值
     * 
     * @return 阈值百分比，未设置则返回默认值
     */
    public int getMemoryCleanThreshold() {
        return mPrefs.getInt(KEY_MEMORY_CLEAN_THRESHOLD, DEFAULT_MEMORY_CLEAN_THRESHOLD);
    }
    
    /**
     * 保存内存清理启用状态
     * 
     * @param enabled true-启用；false-禁用
     */
    public void saveMemoryCleanEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_MEMORY_CLEAN_ENABLED, enabled).apply();
        LogUtils.i("[PrefsManager] Memory clean enabled: " + enabled);
    }
    
    /**
     * 获取内存清理启用状态
     * 
     * @return true-启用；false-禁用，未设置则返回默认值
     */
    public boolean isMemoryCleanEnabled() {
        return mPrefs.getBoolean(KEY_MEMORY_CLEAN_ENABLED, DEFAULT_MEMORY_CLEAN_ENABLED);
    }
    
    /**
     * 保存内存清理间隔
     * 
     * @param seconds 间隔秒数
     */
    public void saveMemoryCleanInterval(int seconds) {
        mPrefs.edit().putInt(KEY_MEMORY_CLEAN_INTERVAL, seconds).apply();
        LogUtils.i("[PrefsManager] Memory clean interval saved: " + seconds + " seconds");
    }
    
    /**
     * 获取内存清理间隔
     * 
     * @return 间隔秒数，未设置则返回默认值
     */
    public int getMemoryCleanInterval() {
        return mPrefs.getInt(KEY_MEMORY_CLEAN_INTERVAL, DEFAULT_MEMORY_CLEAN_INTERVAL);
    }
    
    public void clearAll() {
        mPrefs.edit().clear().apply();
        LogUtils.i("[PrefsManager] All preferences cleared");
    }
}
