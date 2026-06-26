package com.haier.emptyscreen.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.haier.logger.HLogger;

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

    /**
     * SharedPreferences 文件名
     */
    private static final String PREFS_NAME = "empty_screen_prefs";

    /**
     * Device Protected Storage 偏好文件名 - Direct Boot 阶段（用户未解锁）可访问
     *
     * <p>开机关键配置（自定义广播 action、自适应延迟、重试次数/间隔）需在 Direct Boot 阶段读取，
     * 而 CE 存储在解锁前不可访问。这里用独立的 DE 偏好文件存放这些配置的副本，
     * 由 {@link #migrateBootPrefsToDeviceProtectedStorage(Context)} 在解锁后从 CE 同步。</p>
     */
    private static final String DE_PREFS_NAME = "empty_screen_boot_prefs_de";

    /**
     * URL 配置键
     */
    private static final String KEY_URL = "url";

    /**
     * 开机启动延迟键（秒）
     */
    private static final String KEY_BOOT_DELAY_SECONDS = "boot_delay_seconds";

    /**
     * 前台服务触发延迟键（秒）
     */
    private static final String KEY_FOREGROUND_DELAY_SECONDS = "foreground_delay_seconds";

    /**
     * 首次启动标记键
     */
    private static final String KEY_FIRST_LAUNCH = "first_launch";

    /**
     * 是否在设置页面标记键
     */
    private static final String KEY_IS_ON_SETTINGS_PAGE = "is_on_settings_page";

    /**
     * 内存清理阈值键（百分比）
     */
    private static final String KEY_MEMORY_CLEAN_THRESHOLD = "memory_clean_threshold";

    /**
     * 内存清理启用状态键
     */
    private static final String KEY_MEMORY_CLEAN_ENABLED = "memory_clean_enabled";

    /**
     * 内存清理间隔键（秒）
     */
    private static final String KEY_MEMORY_CLEAN_INTERVAL = "memory_clean_interval";

    /**
     * 自适应开机延迟键（秒）- 通过学习收敛到本机最佳拉起时机
     */
    private static final String KEY_ADAPTIVE_DELAY = "adaptive_delay_seconds";

    /**
     * 开机拉起重试次数键
     */
    private static final String KEY_BOOT_RETRY_COUNT = "boot_retry_count";

    /**
     * 开机拉起重试间隔键（秒）
     */
    private static final String KEY_BOOT_RETRY_INTERVAL = "boot_retry_interval";

    /**
     * 上次开机时间戳键（SystemClock.elapsedRealtime）
     */
    private static final String KEY_LAST_BOOT_ELAPSED = "last_boot_elapsed";

    /**
     * 本次开机拉起成功标记键
     */
    private static final String KEY_BOOT_LAUNCH_SUCCESS = "boot_launch_success";

    /**
     * 本次拉起来源标签键（BOOT_COLD / BOOT_BROADCAST / STR_WAKE），用于跨重试、跨进程读回
     */
    private static final String KEY_LAUNCH_SOURCE = "launch_source";

    /**
     * 厂商自定义开机广播 action 列表键（逗号分隔）
     */
    private static final String KEY_CUSTOM_BOOT_ACTIONS = "custom_boot_actions";

    /**
     * 默认 URL（内网测试地址）
     */
    private static final String DEFAULT_URL = "http://10.181.135.178:8088/#/?hospitalCode=5226";
//    private static final String DEFAULT_URL = "https://www.baidu.com/";

    /**
     * 默认开机延迟（5 秒）
     */
    private static final int DEFAULT_BOOT_DELAY = 5;

    /**
     * 默认自适应延迟初始值（秒）- 首次安装时的保守起点
     */
    private static final int DEFAULT_ADAPTIVE_DELAY = 10;

    /**
     * 默认开机拉起重试次数
     */
    private static final int DEFAULT_BOOT_RETRY_COUNT = 5;

    /**
     * 默认开机拉起重试间隔（秒）
     */
    private static final int DEFAULT_BOOT_RETRY_INTERVAL = 10;

    /**
     * 自适应延迟下限（秒）
     */
    private static final int MIN_ADAPTIVE_DELAY = 3;

    /**
     * 自适应延迟上限（秒）
     */
    private static final int MAX_ADAPTIVE_DELAY = 60;

    /**
     * 自适应学习平滑系数（指数移动平均，新值权重）
     */
    private static final float ADAPTIVE_ALPHA = 0.4f;

    /**
     * 默认前台服务延迟（60 秒）
     */
    private static final int DEFAULT_FOREGROUND_DELAY = 60;

    /**
     * 默认内存清理阈值（80%）
     */
    private static final int DEFAULT_MEMORY_CLEAN_THRESHOLD = 95;

    /**
     * 默认启用内存清理
     */
    private static final boolean DEFAULT_MEMORY_CLEAN_ENABLED = true;

    /**
     * 默认内存清理间隔（60 秒）
     */
    private static final int DEFAULT_MEMORY_CLEAN_INTERVAL = 60;

    /**
     * 单例实例
     */
    private static PrefsManager sInstance;

    /**
     * SharedPreferences 对象
     */
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
        HLogger.i("[PrefsManager] URL saved: " + url);
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
        HLogger.i("[PrefsManager] Boot delay saved: " + seconds + " seconds");
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
        HLogger.i("[PrefsManager] Foreground delay saved: " + seconds + " seconds");
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
        HLogger.i("[PrefsManager] On settings page: " + isOnSettingsPage);
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
        HLogger.i("[PrefsManager] Memory clean threshold saved: " + threshold + "%");
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
        HLogger.i("[PrefsManager] Memory clean enabled: " + enabled);
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
        HLogger.i("[PrefsManager] Memory clean interval saved: " + seconds + " seconds");
    }

    /**
     * 获取内存清理间隔
     *
     * @return 间隔秒数，未设置则返回默认值
     */
    public int getMemoryCleanInterval() {
        return mPrefs.getInt(KEY_MEMORY_CLEAN_INTERVAL, DEFAULT_MEMORY_CLEAN_INTERVAL);
    }

    // ==================== 开机自适应延迟相关 ====================

    /**
     * 获取自适应开机延迟时间（秒）
     *
     * <p>该值由历史开机记录学习得到，越用越接近本机真实的"开机进首页"耗时。</p>
     *
     * @return 自适应延迟秒数
     */
    public int getAdaptiveDelaySeconds() {
        return mPrefs.getInt(KEY_ADAPTIVE_DELAY, DEFAULT_ADAPTIVE_DELAY);
    }

    /**
     * 根据本次实际拉起耗时更新自适应延迟（指数移动平均）
     *
     * <p>新延迟 = α × 本次耗时 + (1-α) × 历史延迟，并裁剪到 [下限, 上限]。</p>
     *
     * @param actualElapsedSeconds 本次"开机广播 → 成功进前台"的实际耗时（秒）
     */
    public void updateAdaptiveDelay(int actualElapsedSeconds) {
        if (actualElapsedSeconds <= 0) {
            return;
        }
        int oldDelay = getAdaptiveDelaySeconds();
        float smoothed = ADAPTIVE_ALPHA * actualElapsedSeconds + (1 - ADAPTIVE_ALPHA) * oldDelay;
        int newDelay = Math.round(smoothed);
        if (newDelay < MIN_ADAPTIVE_DELAY) {
            newDelay = MIN_ADAPTIVE_DELAY;
        } else if (newDelay > MAX_ADAPTIVE_DELAY) {
            newDelay = MAX_ADAPTIVE_DELAY;
        }
        mPrefs.edit().putInt(KEY_ADAPTIVE_DELAY, newDelay).apply();
        HLogger.i("[PrefsManager] Adaptive delay updated: " + oldDelay + "s -> " + newDelay
                + "s (actual=" + actualElapsedSeconds + "s)");
    }

    /**
     * 获取开机拉起重试次数
     *
     * @return 重试次数
     */
    public int getBootRetryCount() {
        return mPrefs.getInt(KEY_BOOT_RETRY_COUNT, DEFAULT_BOOT_RETRY_COUNT);
    }

    /**
     * 保存开机拉起重试次数
     *
     * @param count 重试次数
     */
    public void saveBootRetryCount(int count) {
        mPrefs.edit().putInt(KEY_BOOT_RETRY_COUNT, count).apply();
    }

    /**
     * 获取开机拉起重试间隔（秒）
     *
     * @return 重试间隔秒数
     */
    public int getBootRetryInterval() {
        return mPrefs.getInt(KEY_BOOT_RETRY_INTERVAL, DEFAULT_BOOT_RETRY_INTERVAL);
    }

    /**
     * 保存开机拉起重试间隔（秒）
     *
     * @param seconds 重试间隔秒数
     */
    public void saveBootRetryInterval(int seconds) {
        mPrefs.edit().putInt(KEY_BOOT_RETRY_INTERVAL, seconds).apply();
    }

    /**
     * 记录开机广播触发时刻（SystemClock.elapsedRealtime 毫秒）
     *
     * @param elapsedMillis 开机基准时间
     */
    public void saveLastBootElapsed(long elapsedMillis) {
        mPrefs.edit().putLong(KEY_LAST_BOOT_ELAPSED, elapsedMillis).apply();
    }

    /**
     * 获取开机广播触发时刻
     *
     * @return 开机基准时间（毫秒），未记录返回 -1
     */
    public long getLastBootElapsed() {
        return mPrefs.getLong(KEY_LAST_BOOT_ELAPSED, -1L);
    }

    /**
     * 设置本次开机拉起是否已成功（成功后用于取消剩余重试）
     *
     * @param success true-已成功进前台
     */
    public void setBootLaunchSuccess(boolean success) {
        mPrefs.edit().putBoolean(KEY_BOOT_LAUNCH_SUCCESS, success).apply();
    }

    /**
     * 查询本次开机拉起是否已成功
     *
     * @return true-已成功
     */
    public boolean isBootLaunchSuccess() {
        return mPrefs.getBoolean(KEY_BOOT_LAUNCH_SUCCESS, false);
    }

    /**
     * 保存本次拉起来源标签（BOOT_COLD / BOOT_BROADCAST / STR_WAKE）
     *
     * <p>来源在入口确定后存入，供后续重试、跨进程的 LauncherActivity 成功点读回，
     * 使整条拉起链路的日志都能标注"本次是哪种开关机/唤醒拉起的"。</p>
     *
     * @param source 来源标签
     */
    public void saveLaunchSource(String source) {
        mPrefs.edit().putString(KEY_LAUNCH_SOURCE, source).apply();
    }

    /**
     * 读取本次拉起来源标签
     *
     * @return 来源标签，未记录返回 "UNKNOWN"
     */
    public String getLaunchSource() {
        return mPrefs.getString(KEY_LAUNCH_SOURCE, "UNKNOWN");
    }

    /**
     * 保存厂商自定义开机广播 action 列表
     *
     * @param actions action 集合（逗号分隔的字符串）
     */
    public void saveCustomBootActions(String actions) {
        mPrefs.edit().putString(KEY_CUSTOM_BOOT_ACTIONS, actions == null ? "" : actions).apply();
        HLogger.i("[PrefsManager] Custom boot actions saved: " + actions);
    }

    /**
     * 获取厂商自定义开机广播 action 列表
     *
     * @return 逗号分隔的 action 字符串，未设置返回空串
     */
    public String getCustomBootActions() {
        return mPrefs.getString(KEY_CUSTOM_BOOT_ACTIONS, "");
    }

    public void clearAll() {
        mPrefs.edit().clear().apply();
        HLogger.i("[PrefsManager] All preferences cleared");
    }

    // ==================== Direct Boot（DE 存储）支持 ====================
    //
    // BootReceiver 声明了 directBootAware="true" 并监听 LOCKED_BOOT_COMPLETED，
    // 但其读取的 SharedPreferences 默认位于 Credential Encrypted（CE）存储，
    // 在用户解锁前不可访问——读返回默认值、写静默丢弃。
    //
    // 这里将开机关键配置（自定义广播 action、自适应延迟、重试次数/间隔）的副本
    // 存到 Device Protected Storage（DE），供 Direct Boot 阶段读取。
    // 解锁后由 migrateBootPrefsToDeviceProtectedStorage() 从 CE 同步到 DE。

    /**
     * 查询用户凭据存储是否已解锁（Direct Boot 判定）
     */
    public static boolean isUserUnlocked(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return true;
        }
        try {
            android.os.UserManager um =
                    (android.os.UserManager) context.getSystemService(Context.USER_SERVICE);
            return um == null || um.isUserUnlocked();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 获取 DE 存储上下文（Direct Boot 阶段可访问的存储）
     */
    private static SharedPreferences getDEPrefs(Context context) {
        Context deContext = context.createDeviceProtectedStorageContext();
        return deContext.getSharedPreferences(DE_PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 从 DE 存储读取自定义开机广播 action 列表
     *
     * <p>Direct Boot 阶段 CE 存储不可访问时使用。返回的是上次解锁后同步过来的副本。</p>
     */
    public static String getCustomBootActionsDE(Context context) {
        return getDEPrefs(context).getString(KEY_CUSTOM_BOOT_ACTIONS, "");
    }

    /**
     * 从 DE 存储读取自适应开机延迟（秒）
     */
    public static int getAdaptiveDelaySecondsDE(Context context) {
        return getDEPrefs(context).getInt(KEY_ADAPTIVE_DELAY, DEFAULT_ADAPTIVE_DELAY);
    }

    /**
     * 从 DE 存储读取开机拉起重试次数
     */
    public static int getBootRetryCountDE(Context context) {
        return getDEPrefs(context).getInt(KEY_BOOT_RETRY_COUNT, DEFAULT_BOOT_RETRY_COUNT);
    }

    /**
     * 从 DE 存储读取开机拉起重试间隔（秒）
     */
    public static int getBootRetryIntervalDE(Context context) {
        return getDEPrefs(context).getInt(KEY_BOOT_RETRY_INTERVAL, DEFAULT_BOOT_RETRY_INTERVAL);
    }

    /**
     * 将开机关键配置从 CE 存储同步到 DE 存储，供下次 Direct Boot 使用
     *
     * <p>应在应用正常运行（用户已解锁）时调用，例如 Application.onCreate。
     * 仅在已解锁时执行——解锁前 CE 存储不可读，迁移无意义。</p>
     */
    public static void migrateBootPrefsToDeviceProtectedStorage(Context context) {
        if (!isUserUnlocked(context)) {
            HLogger.d("[PrefsManager] Skip DE migration: user not unlocked (Direct Boot)");
            return;
        }
        try {
            PrefsManager cePrefs = getInstance(context);
            SharedPreferences dePrefs = getDEPrefs(context);
            dePrefs.edit()
                    .putString(KEY_CUSTOM_BOOT_ACTIONS, cePrefs.getCustomBootActions())
                    .putInt(KEY_ADAPTIVE_DELAY, cePrefs.getAdaptiveDelaySeconds())
                    .putInt(KEY_BOOT_RETRY_COUNT, cePrefs.getBootRetryCount())
                    .putInt(KEY_BOOT_RETRY_INTERVAL, cePrefs.getBootRetryInterval())
                    .apply();
            HLogger.d("[PrefsManager] Boot prefs migrated to Device Protected Storage");
        } catch (Exception e) {
            HLogger.w("[PrefsManager] DE migration failed: " + e.getMessage());
        }
    }
}
