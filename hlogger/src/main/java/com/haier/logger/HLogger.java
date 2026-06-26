package com.haier.logger;

import android.app.Application;
import android.util.Log;

import com.haier.logger.behavior.BehaviorManager;
import com.haier.logger.behavior.BehaviorEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HLogger {

    public static final int LEVEL_VERBOSE = 2;
    public static final int LEVEL_DEBUG = 3;
    public static final int LEVEL_INFO = 4;
    public static final int LEVEL_WARN = 5;
    public static final int LEVEL_ERROR = 6;

    private static volatile boolean initialized = false;
    private static HLoggerConfig config;
    private static LogPrinter printer;
    private static final ConcurrentHashMap<String, String> headers = new ConcurrentHashMap<>();
    private static int logLevel = LEVEL_DEBUG;
    private static LogRingBuffer ringBuffer;

    public static void init(HLoggerConfig cfg) {
        if (initialized) return;
        config = cfg;
        logLevel = cfg.getLogLevel();
        headers.putAll(cfg.getHeaders());

        printer = new LogPrinter(cfg.isEnableConsoleLog(), cfg.isEnableBorderPrint());
        ringBuffer = new LogRingBuffer(cfg.getRingBufferSize());

        LogWriter.getInstance().init(cfg);

        // 核心日志组件已就绪，立即标记初始化完成。这样即使下面任意可选模块
        // 初始化失败，日志的打印与本地文件写入也不会受到影响。
        initialized = true;

        safeInit("printHeaderBanner", HLogger::printHeaderBanner);

        // 以下均为可选模块，逐个用 try-catch 隔离：任意一个初始化抛异常，
        // 既不会中断其它模块，也不会影响日志本身。

        // Init COS uploader early so CrashCatcher's auto-upload and any later
        // uploads have a valid cosService.
        safeInit("CosUploader", () -> {
            CosUploader.init(cfg);
            LogUploader.cleanStaleUploadZips(cfg.getContext());
        });

        if (cfg.isEnableCrashCatcher()) {
            safeInit("CrashCatcher", () -> CrashCatcher.install(cfg));
        }

        if (cfg.isEnableAnrDetection()) {
            safeInit("AnrWatchdog", () -> new AnrWatchdog(cfg).start());
        }

        if (cfg.isEnableNativeCrash()) {
            safeInit("NativeCrashHandler", () -> NativeCrashHandler.install(cfg.getContext(), cfg));
        }

        if (cfg.isEnableMainLooperCatch()) {
            safeInit("MainLooperCatcher", () -> MainLooperCatcher.install(cfg));
        }

        // MQTT last: uses clientId + triggers remote commands that depend on
        // everything above being ready.
        safeInit("MqttService", () -> MqttService.getInstance().init(cfg.getContext(), cfg));

        // Init behavior logging
        if (cfg.isEnableBehaviorLog()) {
            safeInit("BehaviorManager", () -> BehaviorManager.init(cfg));
        }
    }

    /**
     * 执行可选模块的初始化，捕获一切异常，确保单个模块失败不会中断初始化流程，
     * 也不会影响日志的打印与本地文件写入。
     */
    private static void safeInit(String module, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            Log.e("HLogger", "init module failed: " + module + ", skipped", t);
        }
    }

    // --- Basic log methods with tag ---

    public static void v(String tag, String msg) {
        log(LEVEL_VERBOSE, tag, msg);
    }

    public static void d(String tag, String msg) {
        log(LEVEL_DEBUG, tag, msg);
    }

    public static void i(String tag, String msg) {
        log(LEVEL_INFO, tag, msg);
    }

    public static void w(String tag, String msg) {
        log(LEVEL_WARN, tag, msg);
    }

    public static void e(String tag, String msg) {
        log(LEVEL_ERROR, tag, msg);
    }

    public static void e(String tag, String msg, Throwable t) {
        log(LEVEL_ERROR, tag, msg + "\n" + Log.getStackTraceString(t));
    }

    // --- Basic log methods without tag (use global tag) ---

    public static void v(String msg) {
        log(LEVEL_VERBOSE, null, msg);
    }

    public static void d(String msg) {
        log(LEVEL_DEBUG, null, msg);
    }

    public static void i(String msg) {
        log(LEVEL_INFO, null, msg);
    }

    public static void w(String msg) {
        log(LEVEL_WARN, null, msg);
    }

    public static void e(String msg) {
        log(LEVEL_ERROR, null, msg);
    }

    // --- Multi-type printing ---

    public static void json(String tag, String jsonString) {
        log(LEVEL_DEBUG, tag, LogFormatter.formatJson(jsonString));
    }

    public static void xml(String tag, String xmlString) {
        log(LEVEL_DEBUG, tag, LogFormatter.formatXml(xmlString));
    }

    public static void obj(String tag, Object object) {
        log(LEVEL_DEBUG, tag, LogFormatter.formatObject(object));
    }

    public static void bytes(String tag, byte[] data) {
        log(LEVEL_DEBUG, tag, LogFormatter.formatBytes(data));
    }

    public static void bytes(String tag, byte[] data, int offset, int length) {
        log(LEVEL_DEBUG, tag, LogFormatter.formatBytes(data, offset, length));
    }

    // --- Custom object parsers ---

    public static <T> void registerObjectParser(Class<T> clazz, ObjectParser<T> parser) {
        LogFormatter.registerParser(clazz, parser);
    }

    public static void registerDefaultParser(ObjectParser<Object> parser) {
        LogFormatter.registerDefaultParser(parser);
    }

    // --- Structured event API ---

    public static void event(String category, String name, Map<String, Object> data) {
        if (!initialized) return;
        if (category == null || category.isEmpty()) category = "default";
        if (name == null || name.isEmpty()) return;

        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("time", System.currentTimeMillis());
        eventData.put("category", category);
        eventData.put("name", name);
        eventData.put("deviceCode", config.getDeviceCode());
        eventData.put("appCode", config.getAppCode());
        if (data != null) {
            eventData.put("data", data);
        }

        LogWriter.getInstance().writeEvent(category, eventData);

        if (config.isEnableConsoleLog()) {
            Log.d(config.getGlobalTag() != null ? config.getGlobalTag() : "HLogger",
                    "[pid:" + android.os.Process.myPid() + "] [Event] " + category + "/" + name + " " + (data != null ? data.toString() : ""));
        }
    }

    // --- Control methods ---

    public static void setLogLevel(int level) {
        logLevel = level;
    }

    public static void updateHeader(String key, String value) {
        headers.put(key, value);
    }

    public static void flush() {
        if (initialized) LogWriter.getInstance().flush();
    }

    public static void uploadNow() {
        if (initialized) LogUploader.uploadLogs(config.getContext());
    }

    public static void uploadEventsNow(String category) {
        if (initialized) LogUploader.uploadEvents(config.getContext(), category, null);
    }

    public static String getClientId() {
        return MqttService.getInstance().getClientId();
    }

    public interface AnrListener {
        void onAnr(long timeoutMs, String mainStack, String threadDump);
    }

    public static void setAnrListener(AnrListener listener) {
        AnrWatchdog.setAnrListener(listener);
    }

    public interface CrashListener {
        void onCrash(Thread thread, Throwable throwable, String reportPath, boolean fatal);
    }

    public static void setCrashListener(CrashListener listener) {
        CrashCatcher.setExternalListener(listener);
    }

    public static void reportNonFatal(String tag, Throwable t) {
        if (!initialized || t == null) return;
        CrashCatcher.writeNonFatalReport(config, tag, t);
    }

    // --- Behavior logging API ---

    public static void startBehaviorTracking(Application application) {
        if (!initialized || !config.isEnableBehaviorLog()) return;
        BehaviorManager.getInstance().start(application);
    }

    public static void stopBehaviorTracking(Application application) {
        if (!initialized) return;
        BehaviorManager.getInstance().stop(application);
    }

    public static void behavior(String category, String name, Map<String, Object> data) {
        if (!initialized || !config.isEnableBehaviorLog()) return;
        BehaviorManager.getInstance().trackEvent(category, name, data);
    }

    public static void trackPage(String pageName) {
        if (!initialized || !config.isEnableBehaviorLog()) return;
        BehaviorManager.getInstance().trackPage(pageName);
    }

    public static void trackAction(String target, String action) {
        if (!initialized || !config.isEnableBehaviorLog()) return;
        BehaviorManager.getInstance().trackAction(target, action);
    }

    public static void trackAction(String target, String action, Map<String, Object> data) {
        if (!initialized || !config.isEnableBehaviorLog()) return;
        BehaviorManager.getInstance().trackAction(target, action, data);
    }

    public static void trackBusiness(String name, Map<String, Object> data) {
        if (!initialized || !config.isEnableBehaviorLog()) return;
        BehaviorManager.getInstance().trackBusiness(name, data);
    }

    public static void trackSystem(String name) {
        if (!initialized || !config.isEnableBehaviorLog()) return;
        BehaviorManager.getInstance().trackSystem(name);
    }

    public static void setBehaviorUserId(String userId) {
        if (!initialized) return;
        BehaviorManager.getInstance().setUserId(userId);
    }

    public static void setBehaviorUserType(String userType) {
        if (!initialized) return;
        BehaviorManager.getInstance().setUserType(userType);
    }

    public static String getBehaviorUserId() {
        if (!initialized) return null;
        return BehaviorManager.getInstance().getUserId();
    }

    public static String getBehaviorSessionId() {
        if (!initialized) return null;
        return BehaviorManager.getInstance().getSessionId();
    }

    public static void flushBehavior() {
        if (!initialized) return;
        BehaviorManager.getInstance().flush();
    }

    // --- Internal ---

    private static void log(int level, String tag, String msg) {
        if (!initialized || level < logLevel) return;

        String resolvedTag = resolveTag(tag);
        String threadName = Thread.currentThread().getName();
        String levelStr = getLevelString(level);

        // 文件写入优先且独立保护：无论控制台打印、格式化等环节是否出错，
        // 都要保证日志能落到本地文件。
        if (config.isEnableFileLog()) {
            try {
                String caller = config.isEnableCallStack() ? getCallerInfo() : null;
                LogWriter.getInstance().write(levelStr, resolvedTag, msg, threadName, caller, headers);
            } catch (Throwable ignored) {}
        }

        try {
            ringBuffer.add("[" + levelStr + "/" + resolvedTag + "] [" + threadName + "] " + msg);
        } catch (Throwable ignored) {}

        if (config.isEnableConsoleLog()) {
            try {
                String caller = config.isEnableCallStack() ? getCallerInfo() : null;
                String displayMsg = autoFormat(msg);
                printer.print(level, resolvedTag, displayMsg, headers, threadName, caller);
            } catch (Throwable ignored) {}
        }
    }

    private static String autoFormat(String msg) {
        if (msg == null || msg.length() < 2) return msg;
        String trimmed = msg.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            String formatted = LogFormatter.formatJson(trimmed);
            if (!formatted.startsWith("[Invalid JSON]")) {
                return formatted;
            }
        }
        return msg;
    }

    private static String resolveTag(String tag) {
        String globalTag = config.getGlobalTag();
        if (tag == null || tag.isEmpty()) {
            return globalTag != null ? globalTag : "HLogger";
        }
        if (globalTag != null && !globalTag.isEmpty()) {
            return globalTag + "-" + tag;
        }
        return tag;
    }

    private static String getLevelString(int level) {
        switch (level) {
            case LEVEL_VERBOSE:
                return "V";
            case LEVEL_DEBUG:
                return "D";
            case LEVEL_INFO:
                return "I";
            case LEVEL_WARN:
                return "W";
            case LEVEL_ERROR:
                return "E";
            default:
                return "D";
        }
    }

    private static String getCallerInfo() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 4; i < stackTrace.length; i++) {
            String className = stackTrace[i].getClassName();
            if (!className.equals(HLogger.class.getName())) {
                int dot = className.lastIndexOf('.');
                String simple = dot >= 0 ? className.substring(dot + 1) : className;
                return simple + "." + stackTrace[i].getMethodName()
                        + ":" + stackTrace[i].getLineNumber();
            }
        }
        return null;
    }

    static HLoggerConfig getConfig() {
        return config;
    }

    private static void printHeaderBanner() {
        if (!config.isEnableConsoleLog() || headers.isEmpty()) return;
        StringBuilder sb = new StringBuilder("HLogger started | ");
        boolean first = true;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (!first) sb.append(" | ");
            sb.append(e.getKey()).append("=").append(e.getValue());
            first = false;
        }
        Log.i(config.getGlobalTag() != null ? config.getGlobalTag() : "HLogger",
                "[pid:" + android.os.Process.myPid() + "] " + sb.toString());
    }

    static String[] getRecentLogs() {
        return ringBuffer != null ? ringBuffer.snapshot() : new String[0];
    }
}
