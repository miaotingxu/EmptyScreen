package com.haier.emptyscreen.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日志工具类 - 统一日志输出和文件记录
 * 
 * <p>特点：</p>
 * <ul>
 *   <li>统一TAG: "EmptyScreen"</li>
 *   <li>支持可变参数格式化</li>
 *   <li>日志写入外部存储文件</li>
 *   <li>使用WeakReference防止内存泄漏</li>
 * </ul>
 * 
 * @author EmptyScreen Team
 * @version 1.0
 */
public class LogUtils {
    private static final String TAG = "EmptyScreen";
    private static final int LOG_LEVEL_DEBUG = 0;
    private static final int LOG_LEVEL_INFO = 2;
    private static final int LOG_LEVEL_WARN = 3;
    private static final int LOG_LEVEL_ERROR = 4;
    
    private static final int WRITE_LEVEL = LOG_LEVEL_ERROR;
    private static final int MAX_LOG_FILE_SIZE = 5 * 1024 * 1024;
    private static final int MAX_LOG_FILES = 5;
    private static final int LOG_RETENTION_DAYS = 7;
    
    /** 使用WeakReference包装Context，防止内存泄漏 */
    private static WeakReference<Context> sContextRef;
    private static ExecutorService sExecutor;
    private static SimpleDateFormat sDateFormat;
    private static File sLogDir;
    
    /**
     * 初始化日志工具
     * 
     * <p>使用ApplicationContext避免内存泄漏</p>
     * 
     * @param context 应用上下文（建议使用ApplicationContext）
     */
    public static void init(Context context) {
        if (context == null) {
            Log.e(TAG, "[LogUtils] init failed: context is null");
            return;
        }
        
        Context appContext = context.getApplicationContext();
        sContextRef = new WeakReference<>(appContext);
        sExecutor = Executors.newSingleThreadExecutor();
        sDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        
        File externalDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (externalDir != null) {
            sLogDir = new File(externalDir, "logs");
        } else {
            sLogDir = new File(appContext.getFilesDir(), "logs");
        }
        if (!sLogDir.exists()) {
            sLogDir.mkdirs();
        }
        
        cleanOldLogs();
        
        i("[LogUtils] LogUtils initialized");
    }
    
    /**
     * 获取Context
     * 
     * @return ApplicationContext，可能为null
     */
    private static Context getContext() {
        if (sContextRef != null) {
            return sContextRef.get();
        }
        return null;
    }
    
    /**
     * 获取ApplicationContext
     * 
     * <p>供外部工具类使用，返回ApplicationContext避免内存泄漏</p>
     * 
     * @return ApplicationContext，可能为null
     */
    public static Context getApplicationContext() {
        return getContext();
    }
    
    public static void d(String message) {
        log(LOG_LEVEL_DEBUG, message, null);
    }
    
    public static void d(String format, Object... args) {
        log(LOG_LEVEL_DEBUG, String.format(Locale.getDefault(), format, args), null);
    }
    
    public static void i(String message) {
        log(LOG_LEVEL_INFO, message, null);
    }
    
    public static void i(String format, Object... args) {
        log(LOG_LEVEL_INFO, String.format(Locale.getDefault(), format, args), null);
    }
    
    public static void w(String message) {
        log(LOG_LEVEL_WARN, message, null);
    }
    
    public static void w(String format, Object... args) {
        log(LOG_LEVEL_WARN, String.format(Locale.getDefault(), format, args), null);
    }
    
    public static void e(String message) {
        log(LOG_LEVEL_ERROR, message, null);
    }
    
    public static void e(String format, Object... args) {
        log(LOG_LEVEL_ERROR, String.format(Locale.getDefault(), format, args), null);
    }
    
    public static void e(String message, Throwable throwable) {
        log(LOG_LEVEL_ERROR, message, throwable);
    }
    
    private static void log(int level, String message, Throwable throwable) {
        String fullMessage = message;
        if (throwable != null) {
            fullMessage = message + "\n" + Log.getStackTraceString(throwable);
        }
        
        switch (level) {
            case LOG_LEVEL_DEBUG:
                Log.d(TAG, fullMessage);
                break;
            case LOG_LEVEL_INFO:
                Log.i(TAG, fullMessage);
                break;
            case LOG_LEVEL_WARN:
                Log.w(TAG, fullMessage);
                break;
            case LOG_LEVEL_ERROR:
                Log.e(TAG, fullMessage);
                break;
        }
        
        if (level >= WRITE_LEVEL) {
            writeToFile(level, fullMessage);
        }
    }
    
    private static void writeToFile(int level, String message) {
        if (sExecutor == null || sLogDir == null) {
            return;
        }
        
        sExecutor.execute(() -> {
            try {
                String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                String fileName = "log_" + dateStr + ".txt";
                File logFile = new File(sLogDir, fileName);
                
                if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                    rotateLogFiles();
                    logFile = new File(sLogDir, fileName);
                }
                
                String levelStr;
                switch (level) {
                    case LOG_LEVEL_DEBUG:
                        levelStr = "D";
                        break;
                    case LOG_LEVEL_INFO:
                        levelStr = "I";
                        break;
                    case LOG_LEVEL_WARN:
                        levelStr = "W";
                        break;
                    case LOG_LEVEL_ERROR:
                        levelStr = "E";
                        break;
                    default:
                        levelStr = "U";
                }
                
                String timestamp = sDateFormat.format(new Date());
                String logLine = String.format(Locale.getDefault(), "[%s] %s: %s\n", timestamp, levelStr, message);
                
                try (FileWriter writer = new FileWriter(logFile, true)) {
                    writer.append(logLine);
                    writer.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to write log to file: " + e.getMessage());
            }
        });
    }
    
    private static void rotateLogFiles() {
        File[] files = sLogDir.listFiles((dir, name) -> name.startsWith("log_") && name.endsWith(".txt"));
        if (files != null && files.length >= MAX_LOG_FILES) {
            File oldest = null;
            for (File file : files) {
                if (oldest == null || file.lastModified() < oldest.lastModified()) {
                    oldest = file;
                }
            }
            if (oldest != null) {
                oldest.delete();
            }
        }
    }
    
    private static void cleanOldLogs() {
        if (sLogDir == null) return;
        
        long cutoffTime = System.currentTimeMillis() - (LOG_RETENTION_DAYS * 24L * 60 * 60 * 1000);
        File[] files = sLogDir.listFiles((dir, name) -> name.startsWith("log_") && name.endsWith(".txt"));
        if (files != null) {
            for (File file : files) {
                if (file.lastModified() < cutoffTime) {
                    file.delete();
                }
            }
        }
    }
    
    public static File getLogDir() {
        return sLogDir;
    }
    
    public static void release() {
        if (sExecutor != null) {
            sExecutor.shutdown();
            sExecutor = null;
        }
    }
}
