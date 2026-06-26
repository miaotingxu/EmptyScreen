package com.haier.logger.behavior;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

class SessionManager {

    private static final String PREF_NAME = "hlogger_session";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_SESSION_START_TIME = "session_start_time";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_TYPE = "user_type";

    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000;

    private final SharedPreferences preferences;
    private String currentSessionId;
    private long sessionStartTime;
    private String userId;
    private String userType;

    SessionManager(Context context) {
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadSession();
    }

    private void loadSession() {
        currentSessionId = preferences.getString(KEY_SESSION_ID, null);
        sessionStartTime = preferences.getLong(KEY_SESSION_START_TIME, 0);
        userId = preferences.getString(KEY_USER_ID, null);
        userType = preferences.getString(KEY_USER_TYPE, null);
    }

    String getSessionId() {
        checkSessionTimeout();
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            createNewSession();
        }
        return currentSessionId;
    }

    private void checkSessionTimeout() {
        if (sessionStartTime > 0 && System.currentTimeMillis() - sessionStartTime > SESSION_TIMEOUT_MS) {
            createNewSession();
        }
    }

    void createNewSession() {
        currentSessionId = UUID.randomUUID().toString();
        sessionStartTime = System.currentTimeMillis();
        saveSession();
    }

    void updateSessionTime() {
        sessionStartTime = System.currentTimeMillis();
        preferences.edit().putLong(KEY_SESSION_START_TIME, sessionStartTime).apply();
    }

    void setUserId(String userId) {
        this.userId = userId;
        preferences.edit().putString(KEY_USER_ID, userId).apply();
    }

    String getUserId() {
        return userId;
    }

    void setUserType(String userType) {
        this.userType = userType;
        preferences.edit().putString(KEY_USER_TYPE, userType).apply();
    }

    String getUserType() {
        return userType;
    }

    long getSessionDuration() {
        return System.currentTimeMillis() - sessionStartTime;
    }

    void clearSession() {
        currentSessionId = null;
        sessionStartTime = 0;
        preferences.edit().clear().apply();
    }

    private void saveSession() {
        preferences.edit()
                .putString(KEY_SESSION_ID, currentSessionId)
                .putLong(KEY_SESSION_START_TIME, sessionStartTime)
                .apply();
    }
}