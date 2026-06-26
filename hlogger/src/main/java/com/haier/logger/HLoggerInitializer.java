package com.haier.logger;

import android.app.Application;
import android.content.Context;

import com.haier.logger.behavior.BehaviorManager;

import java.util.ArrayList;
import java.util.List;

public class HLoggerInitializer {

    private final HLoggerConfig.Builder configBuilder;
    private final Application application;
    private boolean enableBehaviorTracking = true;
    private List<String> validationErrors = new ArrayList<>();

    private HLoggerInitializer(Context context) {
        this.configBuilder = new HLoggerConfig.Builder(context);
        if (context instanceof Application) {
            this.application = (Application) context;
        } else {
            this.application = (Application) context.getApplicationContext();
        }
    }

    /**
     * 创建自定义配置初始化器
     *
     * @param context Application Context
     */
    public static HLoggerInitializer with(Context context) {
        return new HLoggerInitializer(context);
    }

    /**
     * 开发调试模式：仅控制台日志，关闭所有监控
     * 适用于开发阶段快速调试
     */
    public static HLoggerInitializer debug(Context context) {
        return new HLoggerInitializer(context)
                .enableConsoleLog(true)
                .enableFileLog(false)
                .logLevel(HLogger.LEVEL_VERBOSE)
                .enableCrashCatcher(false)
                .enableAnrDetection(false)
                .enableNativeCrash(false)
                .enableBehaviorTracking(false);
    }

    /**
     * 生产发布模式：完整监控能力，适合标准生产环境
     * 控制台日志关闭，文件日志开启，全量采集行为数据
     */
    public static HLoggerInitializer release(Context context) {
        return new HLoggerInitializer(context)
                .enableConsoleLog(false)
                .enableFileLog(true)
                .logLevel(HLogger.LEVEL_INFO)
                .enableCrashCatcher(true)
                .enableAnrDetection(true)
                .enableNativeCrash(true)
                .enableBehaviorTracking(true)
                .behaviorSampleRate(100)
                .enableSensitiveFilter(true)
                .retainDays(7)
                .maxStorageSize(50);
    }

    /**
     * 性能优先模式：降低日志频率，适合性能敏感场景
     * 日志级别较高，行为日志采样率降低，存储限制更严格
     */
    public static HLoggerInitializer performance(Context context) {
        return new HLoggerInitializer(context)
                .enableConsoleLog(false)
                .enableFileLog(true)
                .logLevel(HLogger.LEVEL_WARN)
                .enableCrashCatcher(true)
                .enableAnrDetection(true)
                .enableNativeCrash(true)
                .enableBehaviorTracking(true)
                .behaviorSampleRate(50)
                .enableSensitiveFilter(true)
                .retainDays(7)
                .maxStorageSize(50)
                .flushInterval(10000);
    }

    /**
     * 全面监控模式：详细日志和完整监控能力
     * 适合需要深入分析和调试的场景
     */
    public static HLoggerInitializer comprehensive(Context context) {
        return new HLoggerInitializer(context)
                .enableConsoleLog(true)
                .enableFileLog(true)
                .logLevel(HLogger.LEVEL_DEBUG)
                .enableCrashCatcher(true)
                .enableAnrDetection(true)
                .enableNativeCrash(true)
                .enableMainLooperCatch(true)
                .enableBehaviorTracking(true)
                .behaviorSampleRate(100)
                .enableSensitiveFilter(true)
                .retainDays(14)
                .maxStorageSize(100)
                .flushInterval(3000);
    }

    public HLoggerInitializer enableCrashCatcher(boolean enable) {
        configBuilder.enableCrashCatcher(enable);
        return this;
    }

    public HLoggerInitializer enableAnrDetection(boolean enable) {
        configBuilder.enableAnrDetection(enable);
        return this;
    }

    public HLoggerInitializer enableNativeCrash(boolean enable) {
        configBuilder.enableNativeCrash(enable);
        return this;
    }

    public HLoggerInitializer enableMainLooperCatch(boolean enable) {
        configBuilder.enableMainLooperCatch(enable);
        return this;
    }


    public HLoggerInitializer enableBehaviorLog(boolean enable) {
        configBuilder.enableBehaviorLog(enable);
        return this;
    }

    public HLoggerInitializer enableConsoleLog(boolean enable) {
        configBuilder.enableConsoleLog(enable);
        return this;
    }

    public HLoggerInitializer enableFileLog(boolean enable) {
        configBuilder.enableFileLog(enable);
        return this;
    }

    public HLoggerInitializer logLevel(int level) {
        configBuilder.logLevel(level);
        return this;
    }

    public HLoggerInitializer globalTag(String tag) {
        configBuilder.globalTag(tag);
        return this;
    }

    public HLoggerInitializer deviceCode(String deviceCode) {
        configBuilder.deviceCode(deviceCode);
        return this;
    }

    public HLoggerInitializer appCode(String appCode) {
        configBuilder.appCode(appCode);
        return this;
    }

    public HLoggerInitializer clinicCode(String clinicCode) {
        configBuilder.clinicCode(clinicCode);
        return this;
    }

    public HLoggerInitializer mqttBroker(String broker) {
        configBuilder.mqttBroker(broker);
        return this;
    }

    public HLoggerInitializer mqttCredentials(String clientId, String username, String password) {
        configBuilder.mqttClientId(clientId);
        configBuilder.mqttUsername(username);
        configBuilder.mqttPassword(password);
        return this;
    }

    public HLoggerInitializer cosConfig(String region, String bucket, String secretId, String secretKey) {
        configBuilder.cosRegion(region);
        configBuilder.cosBucket(bucket);
        configBuilder.cosSecretId(secretId);
        configBuilder.cosSecretKey(secretKey);
        return this;
    }

    public HLoggerInitializer enableBehaviorTracking(boolean enable) {
        this.enableBehaviorTracking = enable;
        configBuilder.enableBehaviorLog(enable);
        return this;
    }

    public HLoggerInitializer behaviorSampleRate(int rate) {
        configBuilder.behaviorSampleRate(rate);
        return this;
    }

    public HLoggerInitializer enableSensitiveFilter(boolean enable) {
        configBuilder.enableSensitiveFilter(enable);
        return this;
    }

    public HLoggerInitializer addHeader(String key, String value) {
        configBuilder.addHeader(key, value);
        return this;
    }

    public HLoggerInitializer retainDays(int days) {
        configBuilder.retainDays(days);
        return this;
    }

    public HLoggerInitializer maxStorageSize(int sizeMB) {
        configBuilder.maxStorageSize(sizeMB);
        return this;
    }

    public HLoggerInitializer flushInterval(int intervalMs) {
        configBuilder.flushInterval(intervalMs);
        return this;
    }

    public InitializeResult initialize() {
        HLoggerConfig config = configBuilder.build();

        validationErrors.clear();
        validateConfig(config);

        if (!validationErrors.isEmpty()) {
            return InitializeResult.failure(validationErrors);
        }

        try {
            HLogger.init(config);

            if (enableBehaviorTracking && config.isEnableBehaviorLog()) {
                BehaviorManager.getInstance().start(application);
            }

            return InitializeResult.success();
        } catch (Exception e) {
            validationErrors.add("初始化异常: " + e.getMessage());
            return InitializeResult.failure(validationErrors);
        }
    }

    private void validateConfig(HLoggerConfig config) {
        if (config.getRetainDays() < 1 || config.getRetainDays() > 365) {
            validationErrors.add("日志保留天数必须在1-365之间，当前值: " + config.getRetainDays());
        }

        if (config.getMaxStorageSize() < 1 || config.getMaxStorageSize() > 1024) {
            validationErrors.add("最大存储大小必须在1-1024MB之间，当前值: " + config.getMaxStorageSize());
        }

        if (config.getLogLevel() < 2 || config.getLogLevel() > 6) {
            validationErrors.add("日志级别必须在2-6之间，当前值: " + config.getLogLevel());
        }

        if (config.getFlushInterval() < 100 || config.getFlushInterval() > 60000) {
            validationErrors.add("刷新间隔必须在100-60000ms之间，当前值: " + config.getFlushInterval());
        }

        if (config.getBehaviorSampleRate() < 0 || config.getBehaviorSampleRate() > 100) {
            validationErrors.add("行为日志采样率必须在0-100之间，当前值: " + config.getBehaviorSampleRate());
        }

        if (config.getBehaviorFlushInterval() < 100 || config.getBehaviorFlushInterval() > 60000) {
            validationErrors.add("行为日志刷新间隔必须在100-60000ms之间，当前值: " + config.getBehaviorFlushInterval());
        }

        if (config.getMaxBehaviorEventsPerFile() < 10 || config.getMaxBehaviorEventsPerFile() > 10000) {
            validationErrors.add("单文件最大事件数必须在10-10000之间，当前值: " + config.getMaxBehaviorEventsPerFile());
        }

        if (config.getMqttBroker() == null || config.getMqttBroker().isEmpty()) {
            validationErrors.add("MQTT Broker地址不能为空");
        }

        if (config.getCosRegion() == null || config.getCosRegion().isEmpty()) {
            validationErrors.add("COS区域不能为空");
        }

        if (config.getCosBucket() == null || config.getCosBucket().isEmpty()) {
            validationErrors.add("COS存储桶不能为空");
        }
    }

    public static class InitializeResult {
        private final boolean success;
        private final List<String> errors;

        private InitializeResult(boolean success, List<String> errors) {
            this.success = success;
            this.errors = errors;
        }

        public static InitializeResult success() {
            return new InitializeResult(true, new ArrayList<>());
        }

        public static InitializeResult failure(List<String> errors) {
            return new InitializeResult(false, errors);
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            if (errors == null || errors.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < errors.size(); i++) {
                sb.append(i + 1).append(". ").append(errors.get(i));
                if (i < errors.size() - 1) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        }
    }
}