package com.haier.logger;

import android.content.Context;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class HLoggerConfig {

    public static final int FORMAT_PLAIN = 0;
    public static final int FORMAT_JSON = 1;

    private final Context context;
    private final int retainDays;
    private final int maxStorageSize;
    private final File logDir;
    private final int logLevel;
    private final int logFormat;
    private final String globalTag;
    private final Map<String, String> headers;
    private final boolean enableCrashCatcher;
    private final boolean enableAutoUploadCrash;
    private final boolean enableAnrDetection;
    private final boolean enableCallStack;
    private final boolean enableConsoleLog;
    private final boolean enableFileLog;
    private final boolean enableBorderPrint;
    private final boolean enableNativeCrash;
    private final boolean enableMainLooperCatch;
    private final int flushInterval;
    private final int bufferSize;
    private final int ringBufferSize;
    private final long maxLogFileSize;

    // MQTT config
    private final String mqttBroker;
    private final String mqttClientId;
    private final String mqttUsername;
    private final String mqttPassword;

    // COS config
    private final String cosRegion;
    private final String cosBucket;
    private final String cosSecretId;
    private final String cosSecretKey;

    // Identity
    private final String clinicCode;
    private final String deviceCode;
    private final String appCode;

    // Behavior log config
    private final boolean enableBehaviorLog;
    private final int behaviorSampleRate;
    private final boolean enableSensitiveFilter;
    private final int behaviorFlushInterval;
    private final int maxBehaviorEventsPerFile;

    private HLoggerConfig(Builder builder) {
        this.context = builder.context;
        this.retainDays = builder.retainDays;
        this.maxStorageSize = builder.maxStorageSize;
        this.logDir = builder.logDir;
        this.logLevel = builder.logLevel;
        this.logFormat = builder.logFormat;
        this.globalTag = builder.globalTag;
        this.headers = builder.headers;
        this.enableCrashCatcher = builder.enableCrashCatcher;
        this.enableAutoUploadCrash = builder.enableAutoUploadCrash;
        this.enableAnrDetection = builder.enableAnrDetection;
        this.enableCallStack = builder.enableCallStack;
        this.enableConsoleLog = builder.enableConsoleLog;
        this.enableFileLog = builder.enableFileLog;
        this.enableBorderPrint = builder.enableBorderPrint;
        this.enableNativeCrash = builder.enableNativeCrash;
        this.enableMainLooperCatch = builder.enableMainLooperCatch;
        this.flushInterval = builder.flushInterval;
        this.bufferSize = builder.bufferSize;
        this.ringBufferSize = builder.ringBufferSize;
        this.maxLogFileSize = builder.maxLogFileSize;
        this.mqttBroker = builder.mqttBroker;
        this.mqttClientId = builder.mqttClientId;
        this.mqttUsername = builder.mqttUsername;
        this.mqttPassword = builder.mqttPassword;
        this.cosRegion = builder.cosRegion;
        this.cosBucket = builder.cosBucket;
        this.cosSecretId = builder.cosSecretId;
        this.cosSecretKey = builder.cosSecretKey;
        this.clinicCode = builder.clinicCode;
        this.deviceCode = builder.deviceCode;
        this.appCode = builder.appCode;
        this.enableBehaviorLog = builder.enableBehaviorLog;
        this.behaviorSampleRate = builder.behaviorSampleRate;
        this.enableSensitiveFilter = builder.enableSensitiveFilter;
        this.behaviorFlushInterval = builder.behaviorFlushInterval;
        this.maxBehaviorEventsPerFile = builder.maxBehaviorEventsPerFile;
    }

    public Context getContext() {
        return context;
    }

    public int getRetainDays() {
        return retainDays;
    }

    public int getMaxStorageSize() {
        return maxStorageSize;
    }

    public File getLogDir() {
        return logDir;
    }

    public int getLogLevel() {
        return logLevel;
    }

    public int getLogFormat() {
        return logFormat;
    }

    public String getGlobalTag() {
        return globalTag;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public boolean isEnableCrashCatcher() {
        return enableCrashCatcher;
    }

    public boolean isEnableAutoUploadCrash() {
        return enableAutoUploadCrash;
    }

    public boolean isEnableAnrDetection() {
        return enableAnrDetection;
    }

    public boolean isEnableCallStack() {
        return enableCallStack;
    }

    public boolean isEnableConsoleLog() {
        return enableConsoleLog;
    }

    public boolean isEnableFileLog() {
        return enableFileLog;
    }

    public boolean isEnableBorderPrint() {
        return enableBorderPrint;
    }

    public boolean isEnableNativeCrash() {
        return enableNativeCrash;
    }

    public boolean isEnableMainLooperCatch() {
        return enableMainLooperCatch;
    }

    public int getFlushInterval() {
        return flushInterval;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public long getMaxLogFileSize() {
        return maxLogFileSize;
    }

    public String getMqttBroker() {
        return mqttBroker;
    }

    public String getMqttClientId() {
        return mqttClientId;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public String getMqttPassword() {
        return mqttPassword;
    }

    public String getCosRegion() {
        return cosRegion;
    }

    public String getCosBucket() {
        return cosBucket;
    }

    public String getCosSecretId() {
        return cosSecretId;
    }

    public String getCosSecretKey() {
        return cosSecretKey;
    }

    public String getClinicCode() {
        return clinicCode;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public String getAppCode() {
        return appCode;
    }

    // Behavior log getters
    public boolean isEnableBehaviorLog() {
        return enableBehaviorLog;
    }

    public int getBehaviorSampleRate() {
        return behaviorSampleRate;
    }

    public boolean isEnableSensitiveFilter() {
        return enableSensitiveFilter;
    }

    public int getBehaviorFlushInterval() {
        return behaviorFlushInterval;
    }

    public int getMaxBehaviorEventsPerFile() {
        return maxBehaviorEventsPerFile;
    }

    public static class Builder {
        private final Context context;
        private int retainDays = 7;
        private int maxStorageSize = 50;
        private File logDir = null;
        private int logLevel = 3;
        private int logFormat = FORMAT_PLAIN;
        private String globalTag = null;
        private Map<String, String> headers = new HashMap<>();
        private boolean enableCrashCatcher = true;
        private boolean enableAutoUploadCrash = false;
        private boolean enableAnrDetection = true;
        private boolean enableCallStack = false;
        private boolean enableConsoleLog = true;
        private boolean enableFileLog = true;
        private boolean enableBorderPrint = true;
        private boolean enableNativeCrash = true;
        private boolean enableMainLooperCatch = true;
        private int flushInterval = 3000;
        private int bufferSize = 8192;
        private int ringBufferSize = 80;
        private long maxLogFileSize = 10 * 1024 * 1024; // 10MB default

        private String mqttBroker = "tcp://broker.emqx.io:1883";
        private String mqttClientId = null;
        private String mqttUsername = "";
        private String mqttPassword = "";

        private String cosRegion = "ap-shanghai";
        private String cosBucket = "hlogger-1256666242";
        // 敏感凭证不再硬编码，由外部通过 cosSecretId()/cosSecretKey()（或 HLoggerInitializer.cosConfig）注入
        private String cosSecretId = "";
        private String cosSecretKey = "";

        private String clinicCode = null;
        private String deviceCode = null;
        private String appCode = null;

        // Behavior log config defaults
        private boolean enableBehaviorLog = true;
        private int behaviorSampleRate = 100;
        private boolean enableSensitiveFilter = true;
        private int behaviorFlushInterval = 5000;
        private int maxBehaviorEventsPerFile = 1000;

        public Builder(Context context) {
            TokenManager.init(context);
            this.context = context.getApplicationContext();
        }

        public Builder retainDays(int days) {
            this.retainDays = days;
            return this;
        }

        public Builder maxStorageSize(int sizeMB) {
            this.maxStorageSize = sizeMB;
            return this;
        }

        public Builder logDir(File dir) {
            this.logDir = dir;
            return this;
        }

        public Builder logLevel(int level) {
            this.logLevel = level;
            return this;
        }

        public Builder logFormat(int format) {
            this.logFormat = format;
            return this;
        }

        public Builder globalTag(String tag) {
            this.globalTag = tag;
            return this;
        }

        public Builder addHeader(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public Builder enableCrashCatcher(boolean enable) {
            this.enableCrashCatcher = enable;
            return this;
        }

        public Builder enableAutoUploadCrash(boolean enable) {
            this.enableAutoUploadCrash = enable;
            return this;
        }

        public Builder enableAnrDetection(boolean enable) {
            this.enableAnrDetection = enable;
            return this;
        }

        public Builder enableCallStack(boolean enable) {
            this.enableCallStack = enable;
            return this;
        }

        public Builder enableConsoleLog(boolean enable) {
            this.enableConsoleLog = enable;
            return this;
        }

        public Builder enableFileLog(boolean enable) {
            this.enableFileLog = enable;
            return this;
        }

        public Builder enableBorderPrint(boolean enable) {
            this.enableBorderPrint = enable;
            return this;
        }

        public Builder enableNativeCrash(boolean enable) {
            this.enableNativeCrash = enable;
            return this;
        }

        public Builder enableMainLooperCatch(boolean enable) {
            this.enableMainLooperCatch = enable;
            return this;
        }

        public Builder flushInterval(int intervalMs) {
            this.flushInterval = intervalMs;
            return this;
        }

        public Builder bufferSize(int sizeBytes) {
            this.bufferSize = sizeBytes;
            return this;
        }

        public Builder ringBufferSize(int size) {
            this.ringBufferSize = size;
            return this;
        }

        public Builder maxLogFileSize(long sizeBytes) {
            this.maxLogFileSize = sizeBytes;
            return this;
        }

        public Builder mqttBroker(String broker) {
            this.mqttBroker = broker;
            return this;
        }

        public Builder mqttClientId(String clientId) {
            this.mqttClientId = clientId;
            return this;
        }

        public Builder mqttUsername(String username) {
            this.mqttUsername = username;
            return this;
        }

        public Builder mqttPassword(String password) {
            this.mqttPassword = password;
            return this;
        }

        public Builder cosRegion(String region) {
            this.cosRegion = region;
            return this;
        }

        public Builder cosBucket(String bucket) {
            this.cosBucket = bucket;
            return this;
        }

        public Builder cosSecretId(String secretId) {
            this.cosSecretId = secretId;
            return this;
        }

        public Builder cosSecretKey(String secretKey) {
            this.cosSecretKey = secretKey;
            return this;
        }

        public Builder clinicCode(String clinicCode) {
            this.clinicCode = clinicCode;
            return this;
        }

        public Builder deviceCode(String deviceCode) {
            this.deviceCode = deviceCode;
            return this;
        }

        public Builder appCode(String appCode) {
            this.appCode = appCode;
            return this;
        }

        // Behavior log config setters
        public Builder enableBehaviorLog(boolean enable) {
            this.enableBehaviorLog = enable;
            return this;
        }

        public Builder behaviorSampleRate(int rate) {
            this.behaviorSampleRate = rate;
            return this;
        }

        public Builder enableSensitiveFilter(boolean enable) {
            this.enableSensitiveFilter = enable;
            return this;
        }

        public Builder behaviorFlushInterval(int intervalMs) {
            this.behaviorFlushInterval = intervalMs;
            return this;
        }

        public Builder maxBehaviorEventsPerFile(int count) {
            this.maxBehaviorEventsPerFile = count;
            return this;
        }


        public HLoggerConfig build() {
            if (logDir == null) {
                File base = context.getExternalFilesDir(null);
                if (base == null) {
                    base = context.getFilesDir();
                }
                logDir = new File(base, "hlogs");
            } else {
                String appIdentifier = context.getPackageName().replace(".", "_");
                logDir = new File(logDir, "hlogs_" + appIdentifier);
            }

            if (deviceCode == null || deviceCode.isEmpty()) {
                deviceCode = TokenManager.getDeviceNo();
            }

            if (mqttClientId == null || mqttClientId.isEmpty()) {
                String appIdentifier = context.getPackageName();
                mqttClientId = appIdentifier + "_" + deviceCode;
            }
            if (appCode == null || appCode.isEmpty()) {
                appCode = context.getPackageName();
            }
            return new HLoggerConfig(this);
        }
    }


}
