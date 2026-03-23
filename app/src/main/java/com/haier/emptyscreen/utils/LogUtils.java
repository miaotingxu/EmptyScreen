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
 *   <li>统一 TAG: "EmptyScreen"</li>
 *   <li>支持可变参数格式化</li>
 *   <li>日志写入外部存储文件</li>
 *   <li>使用 WeakReference 防止内存泄漏</li>
 * </ul>
 * 
 * <p>日志级别说明：</p>
 * <ul>
 *   <li>DEBUG - 调试信息，详细开发过程信息</li>
 *   <li>INFO - 一般信息，记录程序正常运行状态</li>
 *   <li>WARN - 警告信息，可能存在问题但不影响运行</li>
 *   <li>ERROR - 错误信息，记录异常和严重问题</li>
 * </ul>
 * 
 * @author EmptyScreen Team
 * @version 1.0
 */
public class LogUtils {
    
    /** 日志统一标签 */
    private static final String TAG = "EmptyScreen";
    
    /** 日志级别常量定义 */
    private static final int LOG_LEVEL_DEBUG = 0;
    private static final int LOG_LEVEL_INFO = 2;
    private static final int LOG_LEVEL_WARN = 3;
    private static final int LOG_LEVEL_ERROR = 4;
    
    /** 写入文件的日志级别阈值（ERROR 级别才写入文件） */
    private static final int WRITE_LEVEL = LOG_LEVEL_ERROR;
    
    /** 单个日志文件最大大小（5MB） */
    private static final int MAX_LOG_FILE_SIZE = 5 * 1024 * 1024;
    
    /** 最大日志文件数量 */
    private static final int MAX_LOG_FILES = 5;
    
    /** 日志保留天数 */
    private static final int LOG_RETENTION_DAYS = 7;
    
    /** 使用 WeakReference 包装 Context，防止内存泄漏 */
    private static WeakReference<Context> sContextRef;
    
    /** 异步执行线程池 */
    private static ExecutorService sExecutor;
    
    /** 日期格式化工具 */
    private static SimpleDateFormat sDateFormat;
    
    /** 日志文件目录 */
    private static File sLogDir;
    
    /**
     * 初始化日志工具
     * 
     * <p>使用 ApplicationContext 避免内存泄漏</p>
     * 
     * @param context 应用上下文（建议使用 ApplicationContext）
     */
    public static void init(Context context) {
        if (context == null) {
            Log.e(TAG, "[LogUtils] init failed: context is null");
            return;
        }
        
        // 使用 Application Context 防止内存泄漏
        Context appContext = context.getApplicationContext();
        sContextRef = new WeakReference<>(appContext);
        sExecutor = Executors.newSingleThreadExecutor();
        sDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        
        // 确定日志目录路径
        File externalDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (externalDir != null) {
            sLogDir = new File(externalDir, "logs");
        } else {
            sLogDir = new File(appContext.getFilesDir(), "logs");
        }
        if (!sLogDir.exists()) {
            sLogDir.mkdirs();
        }
        
        // 清理过期日志
        cleanOldLogs();
        
        i("[LogUtils] LogUtils initialized");
    }
    
    /**
     * 获取 Context
     * 
     * @return ApplicationContext，可能为 null
     */
    private static Context getContext() {
        if (sContextRef != null) {
            return sContextRef.get();
        }
        return null;
    }
    
    /**
     * 获取 ApplicationContext
     * 
     * <p>供外部工具类使用，返回 ApplicationContext 避免内存泄漏</p>
     * 
     * @return ApplicationContext，可能为 null
     */
    public static Context getApplicationContext() {
        return getContext();
    }
    
    /**
     * 输出 DEBUG 级别日志
     * 
     * @param message 日志消息
     */
    public static void d(String message) {
        log(LOG_LEVEL_DEBUG, message, null);
    }
    
    /**
     * 输出 DEBUG 级别日志（支持格式化）
     * 
     * @param format 格式化字符串
     * @param args 可变参数
     */
    public static void d(String format, Object... args) {
        log(LOG_LEVEL_DEBUG, String.format(Locale.getDefault(), format, args), null);
    }
    
    /**
     * 输出 INFO 级别日志
     * 
     * @param message 日志消息
     */
    public static void i(String message) {
        log(LOG_LEVEL_INFO, message, null);
    }
    
    /**
     * 输出 INFO 级别日志（支持格式化）
     * 
     * @param format 格式化字符串
     * @param args 可变参数
     */
    public static void i(String format, Object... args) {
        log(LOG_LEVEL_INFO, String.format(Locale.getDefault(), format, args), null);
    }
    
    /**
     * 输出 WARN 级别日志
     * 
     * @param message 日志消息
     */
    public static void w(String message) {
        log(LOG_LEVEL_WARN, message, null);
    }
    
    /**
     * 输出 WARN 级别日志（支持格式化）
     * 
     * @param format 格式化字符串
     * @param args 可变参数
     */
    public static void w(String format, Object... args) {
        log(LOG_LEVEL_WARN, String.format(Locale.getDefault(), format, args), null);
    }
    
    /**
     * 输出 ERROR 级别日志
     * 
     * @param message 日志消息
     */
    public static void e(String message) {
        log(LOG_LEVEL_ERROR, message, null);
    }
    
    /**
     * 输出 ERROR 级别日志（支持格式化）
     * 
     * @param format 格式化字符串
     * @param args 可变参数
     */
    public static void e(String format, Object... args) {
        log(LOG_LEVEL_ERROR, String.format(Locale.getDefault(), format, args), null);
    }
    
    /**
     * 输出 ERROR 级别日志（包含异常堆栈）
     * 
     * @param message 日志消息
     * @param throwable 异常对象
     */
    public static void e(String message, Throwable throwable) {
        log(LOG_LEVEL_ERROR, message, throwable);
    }
    
    /**
     * 核心日志方法 - 根据级别输出日志并决定是否写入文件
     * 
     * @param level 日志级别
     * @param message 日志消息
     * @param throwable 异常对象（可选）
     */
    private static void log(int level, String message, Throwable throwable) {
        String fullMessage = message;
        if (throwable != null) {
            fullMessage = message + "\n" + Log.getStackTraceString(throwable);
        }
        
        // 根据级别选择对应的 Log 方法
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
        
        // 达到写入级别的日志写入文件
        if (level >= WRITE_LEVEL) {
            writeToFile(level, fullMessage);
        }
    }
    
    /**
     * 将日志异步写入文件
     * 
     * <p>使用线程池异步执行，避免阻塞主线程</p>
     * 
     * @param level 日志级别
     * @param message 日志消息
     */
    private static void writeToFile(int level, String message) {
        if (sExecutor == null || sLogDir == null) {
            return;
        }
        
        sExecutor.execute(() -> {
            try {
                // 生成日志文件名（按日期）
                String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                String fileName = "log_" + dateStr + ".txt";
                File logFile = new File(sLogDir, fileName);
                
                // 检查文件大小，超过限制则轮转
                if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                    rotateLogFiles();
                    logFile = new File(sLogDir, fileName);
                }
                
                // 转换日志级别为字符串
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
                
                // 格式化日志行
                String timestamp = sDateFormat.format(new Date());
                String logLine = String.format(Locale.getDefault(), "[%s] %s: %s\n", timestamp, levelStr, message);
                
                // 追加写入文件
                try (FileWriter writer = new FileWriter(logFile, true)) {
                    writer.append(logLine);
                    writer.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to write log to file: " + e.getMessage());
            }
        });
    }
    
    /**
     * 轮转日志文件 - 删除最旧的文件以控制数量
     */
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
    
    /**
     * 清理超过保留天数的旧日志
     */
    private static void cleanOldLogs() {
        if (sLogDir == null) return;
        
        // 计算截止时间
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
    
    /**
     * 获取日志目录
     * 
     * @return 日志文件目录
     */
    public static File getLogDir() {
        return sLogDir;
    }
    
    /**
     * 释放资源 - 关闭线程池
     */
    public static void release() {
        if (sExecutor != null) {
            sExecutor.shutdown();
            sExecutor = null;
        }
    }
}
