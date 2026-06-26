package com.haier.logger.behavior;

import android.app.Application;

import com.haier.logger.HLoggerConfig;

import java.util.Map;

public class BehaviorManager {

    private static volatile BehaviorManager instance;
    private final BehaviorProcessor behaviorProcessor;
    private final PageTracker pageTracker;
    private final HLoggerConfig config;

    private BehaviorManager(HLoggerConfig config) {
        this.config = config;
        this.behaviorProcessor = new BehaviorProcessor(config);
        this.pageTracker = new PageTracker(behaviorProcessor);
    }

    public static BehaviorManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BehaviorManager has not been initialized. Call init() first.");
        }
        return instance;
    }

    public static void init(HLoggerConfig config) {
        if (instance == null) {
            synchronized (BehaviorManager.class) {
                if (instance == null) {
                    instance = new BehaviorManager(config);
                }
            }
        }
    }

    public void start(Application application) {
        pageTracker.start(application);
    }

    public void stop(Application application) {
        pageTracker.stop(application);
    }

    public void trackEvent(String category, String name, Map<String, Object> data) {
        BehaviorEvent event = new BehaviorEvent.Builder()
                .category(category)
                .name(name)
                .data(data)
                .build();
        behaviorProcessor.processEvent(event);
    }

    public void trackPage(String pageName) {
        BehaviorEvent event = new BehaviorEvent.Builder()
                .category(BehaviorEvent.CATEGORY_PAGE)
                .name(BehaviorEvent.EVENT_PAGE_OPEN)
                .pageName(pageName)
                .build();
        behaviorProcessor.processEvent(event);
    }

    public void trackAction(String target, String action) {
        BehaviorEvent event = new BehaviorEvent.Builder()
                .category(BehaviorEvent.CATEGORY_ACTION)
                .name(action)
                .target(target)
                .build();
        behaviorProcessor.processEvent(event);
    }

    public void trackAction(String target, String action, Map<String, Object> data) {
        BehaviorEvent event = new BehaviorEvent.Builder()
                .category(BehaviorEvent.CATEGORY_ACTION)
                .name(action)
                .target(target)
                .data(data)
                .build();
        behaviorProcessor.processEvent(event);
    }

    public void trackBusiness(String name, Map<String, Object> data) {
        BehaviorEvent event = new BehaviorEvent.Builder()
                .category(BehaviorEvent.CATEGORY_BUSINESS)
                .name(name)
                .data(data)
                .build();
        behaviorProcessor.processEvent(event);
    }

    public void trackSystem(String name) {
        BehaviorEvent event = new BehaviorEvent.Builder()
                .category(BehaviorEvent.CATEGORY_SYSTEM)
                .name(name)
                .build();
        behaviorProcessor.processEvent(event);
    }

    public void setUserId(String userId) {
        behaviorProcessor.setUserId(userId);
    }

    public void setUserType(String userType) {
        behaviorProcessor.setUserType(userType);
    }

    public String getUserId() {
        return behaviorProcessor.getUserId();
    }

    public String getSessionId() {
        return behaviorProcessor.getSessionId();
    }

    public String getCurrentPage() {
        return pageTracker.getCurrentPage();
    }

    public void flush() {
        behaviorProcessor.flush();
    }

    void shutdown() {
        behaviorProcessor.shutdown();
    }
}