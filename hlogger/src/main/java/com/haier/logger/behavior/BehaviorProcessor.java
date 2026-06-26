package com.haier.logger.behavior;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import com.haier.logger.HLoggerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class BehaviorProcessor {

    private final HLoggerConfig config;
    private final SessionManager sessionManager;
    private final List<BehaviorEvent> eventBuffer;
    private final ExecutorService writeExecutor;
    private final ScheduledExecutorService flushScheduler;
    private volatile String userId;
    private volatile String userType;

    BehaviorProcessor(HLoggerConfig config) {
        this.config = config;
        this.sessionManager = new SessionManager(config.getContext());
        this.eventBuffer = new ArrayList<>();
        this.writeExecutor = Executors.newSingleThreadExecutor();
        this.flushScheduler = Executors.newScheduledThreadPool(1);
        startFlushScheduler();
    }

    private void startFlushScheduler() {
        flushScheduler.scheduleAtFixedRate(() -> {
            if (!eventBuffer.isEmpty()) {
                flushEvents();
            }
        }, config.getBehaviorFlushInterval(), config.getBehaviorFlushInterval(), TimeUnit.MILLISECONDS);
    }

    void processEvent(BehaviorEvent event) {
        if (!config.isEnableBehaviorLog()) {
            return;
        }

        if (!isSampled()) {
            return;
        }

        BehaviorEvent enrichedEvent = enrichEvent(event);

        if (config.isEnableSensitiveFilter()) {
            filterSensitiveData(enrichedEvent);
        }

        synchronized (eventBuffer) {
            eventBuffer.add(enrichedEvent);
            if (eventBuffer.size() >= config.getMaxBehaviorEventsPerFile()) {
                flushEvents();
            }
        }
    }

    private boolean isSampled() {
        int rate = config.getBehaviorSampleRate();
        if (rate >= 100) {
            return true;
        }
        return (int) (Math.random() * 100) < rate;
    }

    private BehaviorEvent enrichEvent(BehaviorEvent event) {
        return new BehaviorEvent.Builder()
                .eventId(event.getEventId())
                .category(event.getCategory())
                .name(event.getName())
                .timestamp(event.getTimestamp())
                .sessionId(sessionManager.getSessionId())
                .userId(userId != null ? userId : sessionManager.getUserId())
                .userType(userType != null ? userType : sessionManager.getUserType())
                .pageName(event.getPageName())
                .prevPageName(event.getPrevPageName())
                .duration(event.getDuration())
                .target(event.getTarget())
                .targetText(event.getTargetText())
                .data(event.getData())
                .deviceCode(config.getDeviceCode())
                .appCode(config.getAppCode())
                .appVersion(getAppVersion())
                .osVersion(getOsVersion())
                .networkType(getNetworkType())
                .build();
    }

    private void filterSensitiveData(BehaviorEvent event) {
        String[] sensitiveKeys = {"password", "token", "phone", "mobile", "email", "idCard", "bankCard"};
        if (event.getData() != null) {
            for (String key : sensitiveKeys) {
                if (event.getData().containsKey(key)) {
                    event.getData().put(key, "***");
                }
            }
        }
        if (event.getTargetText() != null) {
            for (String key : sensitiveKeys) {
                if (event.getTargetText().toLowerCase().contains(key)) {
                    event.getData().put("targetText", "***");
                }
            }
        }
    }

    private void flushEvents() {
        List<BehaviorEvent> eventsToWrite;
        synchronized (eventBuffer) {
            eventsToWrite = new ArrayList<>(eventBuffer);
            eventBuffer.clear();
        }

        writeExecutor.execute(() -> {
            for (BehaviorEvent event : eventsToWrite) {
                writeEventToFile(event);
            }
        });
    }

    private void writeEventToFile(BehaviorEvent event) {
        Map<String, Object> eventData = event.toMap();
        com.haier.logger.HLogger.event("behavior", event.getCategory() + "/" + event.getName(), eventData);
    }

    void setUserId(String userId) {
        this.userId = userId;
        sessionManager.setUserId(userId);
    }

    void setUserType(String userType) {
        this.userType = userType;
        sessionManager.setUserType(userType);
    }

    String getUserId() {
        return userId != null ? userId : sessionManager.getUserId();
    }

    String getSessionId() {
        return sessionManager.getSessionId();
    }

    void flush() {
        flushEvents();
    }

    void shutdown() {
        flushScheduler.shutdown();
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
        }
    }

    private String getAppVersion() {
        try {
            PackageInfo info = config.getContext().getPackageManager()
                    .getPackageInfo(config.getContext().getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    private String getOsVersion() {
        return Build.VERSION.RELEASE;
    }

    private String getNetworkType() {
        try {
            ConnectivityManager cm = (ConnectivityManager) config.getContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null) {
                return activeNetwork.getTypeName();
            }
        } catch (Exception e) {
            // ignore
        }
        return "unknown";
    }
}