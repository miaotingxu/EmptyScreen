package com.haier.logger;

import android.os.Build;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class CrashCatcher implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashCatcher[pid:" + android.os.Process.myPid() + "]";
    private static final long NON_FATAL_DEDUP_WINDOW_MS = 30_000L;
    private static final int NON_FATAL_DEDUP_MAX = 256;
    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final HLoggerConfig config;

    private static volatile HLogger.CrashListener externalListener;
    private static volatile HLoggerConfig staticConfig;
    private static final ConcurrentHashMap<String, Long> nonFatalLastSeen = new ConcurrentHashMap<>();

    private CrashCatcher(HLoggerConfig config) {
        this.config = config;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    static void install(HLoggerConfig config) {
        staticConfig = config;
        CrashCatcher catcher = new CrashCatcher(config);
        Thread.setDefaultUncaughtExceptionHandler(catcher);

        if (config.isEnableAutoUploadCrash()) {
            checkAndUploadPendingCrashes(config);
        }
    }

    static void setExternalListener(HLogger.CrashListener listener) {
        externalListener = listener;
    }

    private final AtomicBoolean handling = new AtomicBoolean(false);

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        if (!handling.compareAndSet(false, true)) {
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
            return;
        }

        String reportPath = null;
        try {
            reportPath = writeReport("crash_", thread, throwable, "CRASH REPORT", null);
            writeToLog(thread, throwable);
            LogWriter.getInstance().flushSync();
        } catch (Exception ignored) {}

        HLogger.CrashListener l = externalListener;
        if (l != null) {
            try { l.onCrash(thread, throwable, reportPath, true); } catch (Throwable ignored) {}
        }

        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }

    static String writeNonFatalReport(HLoggerConfig config, String tag, Throwable t) {
        if (config == null) return null;
        if (isDuplicateNonFatal(t)) return null;
        Thread thread = Thread.currentThread();
        String reportPath = null;
        try {
            reportPath = writeReportStatic(config, "nonfatal_", thread, t, "NON-FATAL REPORT", tag);
            String msg = "NON-FATAL in thread \"" + thread.getName() + "\" tag=" + tag + "\n"
                    + getStackTraceStringStatic(t);
            android.util.Log.w(TAG, msg);
            LogWriter.getInstance().write("W", tag != null ? tag : TAG, msg,
                    thread.getName(), null, null);
        } catch (Exception ignored) {}

        HLogger.CrashListener l = externalListener;
        if (l != null) {
            try { l.onCrash(thread, t, reportPath, false); } catch (Throwable ignored) {}
        }
        return reportPath;
    }

    private static boolean isDuplicateNonFatal(Throwable t) {
        if (t == null) return false;
        String sig = signatureOf(t);
        long now = System.currentTimeMillis();
        Long last = nonFatalLastSeen.get(sig);
        if (last != null && now - last < NON_FATAL_DEDUP_WINDOW_MS) {
            return true;
        }
        if (nonFatalLastSeen.size() > NON_FATAL_DEDUP_MAX) {
            nonFatalLastSeen.entrySet().removeIf(
                    e -> now - e.getValue() >= NON_FATAL_DEDUP_WINDOW_MS);
        }
        nonFatalLastSeen.put(sig, now);
        return false;
    }

    private static String signatureOf(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getName()).append('|');
        String m = t.getMessage();
        if (m != null) sb.append(m.length() > 80 ? m.substring(0, 80) : m);
        StackTraceElement[] st = t.getStackTrace();
        if (st != null && st.length > 0) {
            sb.append('|').append(st[0].getClassName())
              .append('.').append(st[0].getMethodName())
              .append(':').append(st[0].getLineNumber());
        }
        return sb.toString();
    }

    private String writeReport(String prefix, Thread thread, Throwable throwable,
                               String header, String tag) {
        return writeReportStatic(config, prefix, thread, throwable, header, tag);
    }

    private static String writeReportStatic(HLoggerConfig config, String prefix, Thread thread,
                                            Throwable throwable, String header, String tag) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss_SSS", Locale.US).format(new Date());
        String suffix = "_" + (System.nanoTime() & 0xFFFFFF);
        File file = new File(config.getLogDir(), prefix + timestamp + suffix + ".txt");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("====== " + header + " ======\n");
            writer.write("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + "\n");
            writer.write("Device: " + Build.MANUFACTURER + " " + Build.MODEL
                         + " (Android " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + ")\n");
            if (tag != null) writer.write("Tag: " + tag + "\n");
            writer.write("\n");

            String deviceInfo = DeviceInfo.collect(config.getContext());
            writer.write(deviceInfo);
            writer.write("\n");

            writer.write("Thread: " + thread.getName() + "\n");
            writer.write(getStackTraceStringStatic(throwable));
            writer.write("\n");

            String[] recentLogs = HLogger.getRecentLogs();
            if (recentLogs.length > 0) {
                writer.write("====== RECENT LOGS (" + recentLogs.length + ") ======\n");
                for (String log : recentLogs) {
                    writer.write("  " + log + "\n");
                }
                writer.write("\n");
            }

            writer.write("====== ALL THREADS ======\n");
            Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
            for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
                Thread t = entry.getKey();
                if (t == thread) continue;
                writer.write("Thread: " + t.getName() + " (" + t.getState() + ")\n");
                for (StackTraceElement element : entry.getValue()) {
                    writer.write("    at " + element.toString() + "\n");
                }
                writer.write("\n");
            }
            return file.getAbsolutePath();
        } catch (IOException ignored) {
            return null;
        }
    }

    private void writeToLog(Thread thread, Throwable throwable) {
        String msg = "FATAL EXCEPTION in thread \"" + thread.getName() + "\"\n"
                     + getStackTraceStringStatic(throwable);
        android.util.Log.e(TAG, msg);
        LogWriter.getInstance().write("E", TAG, msg, thread.getName(), null, null);
    }

    private static String getStackTraceStringStatic(Throwable t) {
        if (t == null) return "(null throwable)\n";
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append("\n");
        for (StackTraceElement element : t.getStackTrace()) {
            sb.append("    at ").append(element.toString()).append("\n");
        }
        Throwable cause = t.getCause();
        while (cause != null) {
            sb.append("Caused by: ").append(cause.toString()).append("\n");
            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append("    at ").append(element.toString()).append("\n");
            }
            cause = cause.getCause();
        }
        return sb.toString();
    }

    private static void checkAndUploadPendingCrashes(HLoggerConfig config) {
        File[] reports = LogWriter.getInstance().getAllReportFiles();
        if (reports.length > 0) {
            LogUploader.uploadLogs(config.getContext());
        }
    }
}
