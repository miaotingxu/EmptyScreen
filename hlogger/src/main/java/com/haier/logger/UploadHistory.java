package com.haier.logger;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.Map;

public class UploadHistory {

    private static final String PREFS_NAME = "hlogger_upload_history";
    private final SharedPreferences prefs;

    public UploadHistory(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean shouldUpload(File file) {
        long recordedSize = prefs.getLong(file.getName(), -1);
        if (recordedSize == -1) return true;
        return file.length() > recordedSize;
    }

    public void markUploaded(File file) {
        prefs.edit().putLong(file.getName(), file.length()).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    public int getRecordCount() {
        Map<String, ?> all = prefs.getAll();
        return all != null ? all.size() : 0;
    }
}
