package com.haier.logger;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

class AnrWatchdog extends Thread {

    private static final String TAG = "AnrWatchdog[pid:" + android.os.Process.myPid() + "]";
    private static final long DEFAULT_TIMEOUT = 5000;

    private static volatile HLogger.AnrListener externalListener;

    static void setAnrListener(HLogger.AnrListener listener) {
        externalListener = listener;
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final long timeout;
    private final HLoggerConfig config;
    private volatile boolean responded;
    private volatile boolean stopped;
    private static final long COOLDOWN = 30_000;
    private long lastAnrTime = 0;

    AnrWatchdog(HLoggerConfig config) {
        this(config, DEFAULT_TIMEOUT);
    }

    AnrWatchdog(HLoggerConfig config, long timeoutMs) {
        super("AnrWatchdog");
        setDaemon(true);
        this.config = config;
        this.timeout = timeoutMs;
    }

    @Override
    public void run() {
        while (!stopped && !isInterrupted()) {
            responded = false;
            mainHandler.post(() -> responded = true);

            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                break;
            }

            if (!responded) {
                long now = System.currentTimeMillis();
                if (now - lastAnrTime > COOLDOWN) {
                    lastAnrTime = now;
                    onAnrDetected();
                }
            }
        }
    }

    private void onAnrDetected() {
        Log.e(TAG, "ANR detected! Main thread not responding for " + timeout + "ms");

        String dump = dumpThreads();
        LogWriter.getInstance().write("E", TAG, "ANR DETECTED\n" + dump,
                "AnrWatchdog", null, null);
        LogWriter.getInstance().flushSync();

        writeAnrFile(dump);

        HLogger.AnrListener l = externalListener;
        if (l != null) {
            try {
                String mainStack = extractMainStack();
                l.onAnr(timeout, mainStack, dump);
            } catch (Throwable ignored) {}
        }
    }

    private String extractMainStack() {
        StringBuilder sb = new StringBuilder();
        Thread mainThread = Looper.getMainLooper().getThread();
        StackTraceElement[] stack = mainThread.getStackTrace();
        int limit = Math.min(stack.length, 30);
        for (int i = 0; i < limit; i++) {
            sb.append(stack[i].toString()).append("\n");
        }
        return sb.toString();
    }

    private void writeAnrFile(String threadDump) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss_SSS", Locale.US).format(new Date());
        String suffix = "_" + (System.nanoTime() & 0xFFFFFF);
        File anrFile = new File(config.getLogDir(), "anr_" + timestamp + suffix + ".txt");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(anrFile))) {
            writer.write("====== ANR REPORT ======\n");
            writer.write("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + "\n");
            writer.write("Timeout: " + timeout + "ms\n\n");

            String[] recentLogs = HLogger.getRecentLogs();
            if (recentLogs.length > 0) {
                writer.write("====== RECENT LOGS (" + recentLogs.length + ") ======\n");
                for (String log : recentLogs) {
                    writer.write("  " + log + "\n");
                }
                writer.write("\n");
            }

            writer.write("====== THREAD DUMP ======\n");
            writer.write(threadDump);
        } catch (IOException ignored) {}
    }

    private String dumpThreads() {
        StringBuilder sb = new StringBuilder();
        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();

        Thread mainThread = Looper.getMainLooper().getThread();
        StackTraceElement[] mainStack = allThreads.get(mainThread);
        if (mainStack != null) {
            sb.append("Main Thread (BLOCKED):\n");
            for (StackTraceElement e : mainStack) {
                sb.append("    at ").append(e.toString()).append("\n");
            }
            sb.append("\n");
        }

        for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
            if (entry.getKey() == mainThread) continue;
            Thread t = entry.getKey();
            sb.append("Thread: ").append(t.getName()).append(" (").append(t.getState()).append(")\n");
            for (StackTraceElement e : entry.getValue()) {
                sb.append("    at ").append(e.toString()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    void stopWatching() {
        stopped = true;
        interrupt();
    }
}