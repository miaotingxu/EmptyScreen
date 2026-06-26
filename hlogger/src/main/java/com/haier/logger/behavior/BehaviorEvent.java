package com.haier.logger.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BehaviorEvent {

    public static final String CATEGORY_PAGE = "page";
    public static final String CATEGORY_ACTION = "action";
    public static final String CATEGORY_BUSINESS = "business";
    public static final String CATEGORY_SYSTEM = "system";

    public static final String EVENT_PAGE_OPEN = "open";
    public static final String EVENT_PAGE_CLOSE = "close";
    public static final String EVENT_PAGE_ENTER = "enter";
    public static final String EVENT_PAGE_LEAVE = "leave";

    public static final String EVENT_ACTION_CLICK = "click";
    public static final String EVENT_ACTION_SCROLL = "scroll";
    public static final String EVENT_ACTION_SWIPE = "swipe";
    public static final String EVENT_ACTION_INPUT = "input";
    public static final String EVENT_ACTION_SELECT = "select";

    public static final String EVENT_BUSINESS_LOGIN = "login";
    public static final String EVENT_BUSINESS_LOGOUT = "logout";
    public static final String EVENT_BUSINESS_PURCHASE = "purchase";
    public static final String EVENT_BUSINESS_SHARE = "share";
    public static final String EVENT_BUSINESS_COLLECT = "collect";
    public static final String EVENT_BUSINESS_SEARCH = "search";

    public static final String EVENT_SYSTEM_FOREGROUND = "foreground";
    public static final String EVENT_SYSTEM_BACKGROUND = "background";
    public static final String EVENT_SYSTEM_CRASH = "crash";
    public static final String EVENT_SYSTEM_ANR = "anr";

    private String eventId;
    private String category;
    private String name;
    private long timestamp;
    private String sessionId;
    private String userId;
    private String userType;
    private String pageName;
    private String prevPageName;
    private long duration;
    private String target;
    private String targetText;
    private Map<String, Object> data;
    private String deviceCode;
    private String appCode;
    private String appVersion;
    private String osVersion;
    private String networkType;

    private BehaviorEvent(Builder builder) {
        this.eventId = builder.eventId != null ? builder.eventId : UUID.randomUUID().toString();
        this.category = builder.category;
        this.name = builder.name;
        this.timestamp = builder.timestamp > 0 ? builder.timestamp : System.currentTimeMillis();
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.userType = builder.userType;
        this.pageName = builder.pageName;
        this.prevPageName = builder.prevPageName;
        this.duration = builder.duration;
        this.target = builder.target;
        this.targetText = builder.targetText;
        this.data = builder.data != null ? builder.data : new HashMap<>();
        this.deviceCode = builder.deviceCode;
        this.appCode = builder.appCode;
        this.appVersion = builder.appVersion;
        this.osVersion = builder.osVersion;
        this.networkType = builder.networkType;
    }

    public String getEventId() { return eventId; }
    public String getCategory() { return category; }
    public String getName() { return name; }
    public long getTimestamp() { return timestamp; }
    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getUserType() { return userType; }
    public String getPageName() { return pageName; }
    public String getPrevPageName() { return prevPageName; }
    public long getDuration() { return duration; }
    public String getTarget() { return target; }
    public String getTargetText() { return targetText; }
    public Map<String, Object> getData() { return data; }
    public String getDeviceCode() { return deviceCode; }
    public String getAppCode() { return appCode; }
    public String getAppVersion() { return appVersion; }
    public String getOsVersion() { return osVersion; }
    public String getNetworkType() { return networkType; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("category", category);
        map.put("name", name);
        map.put("timestamp", timestamp);
        map.put("sessionId", sessionId);
        map.put("userId", userId);
        map.put("userType", userType);
        map.put("pageName", pageName);
        map.put("prevPageName", prevPageName);
        map.put("duration", duration);
        map.put("target", target);
        map.put("targetText", targetText);
        map.put("data", data);
        map.put("deviceCode", deviceCode);
        map.put("appCode", appCode);
        map.put("appVersion", appVersion);
        map.put("osVersion", osVersion);
        map.put("networkType", networkType);
        return map;
    }

    public static class Builder {
        private String eventId;
        private String category;
        private String name;
        private long timestamp;
        private String sessionId;
        private String userId;
        private String userType;
        private String pageName;
        private String prevPageName;
        private long duration;
        private String target;
        private String targetText;
        private Map<String, Object> data;
        private String deviceCode;
        private String appCode;
        private String appVersion;
        private String osVersion;
        private String networkType;

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder userType(String userType) {
            this.userType = userType;
            return this;
        }

        public Builder pageName(String pageName) {
            this.pageName = pageName;
            return this;
        }

        public Builder prevPageName(String prevPageName) {
            this.prevPageName = prevPageName;
            return this;
        }

        public Builder duration(long duration) {
            this.duration = duration;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder targetText(String targetText) {
            this.targetText = targetText;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Builder addData(String key, Object value) {
            if (this.data == null) {
                this.data = new HashMap<>();
            }
            this.data.put(key, value);
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

        public Builder appVersion(String appVersion) {
            this.appVersion = appVersion;
            return this;
        }

        public Builder osVersion(String osVersion) {
            this.osVersion = osVersion;
            return this;
        }

        public Builder networkType(String networkType) {
            this.networkType = networkType;
            return this;
        }

        public BehaviorEvent build() {
            if (category == null || category.isEmpty()) {
                throw new IllegalArgumentException("category is required");
            }
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name is required");
            }
            return new BehaviorEvent(this);
        }
    }
}