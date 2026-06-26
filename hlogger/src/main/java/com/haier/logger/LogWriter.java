package com.haier.logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LogWriter {

    private static LogWriter instance;
    private File logDir;
    private int retainDays;
    private int maxStorageSizeMB;
    private int logFormat;
    private int bufferSize;
    private long maxLogFileSize;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        }
    };
    private final ThreadLocal<SimpleDateFormat> timeFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
        }
    };

    private StringBuilder buffer;
    private String currentFileName;
    private int currentFileIndex = 0;

    private final Map<String, EventFileState> eventStates = new ConcurrentHashMap<>();

    private static class EventFileState {
        String currentFileName;
        int currentFileIndex = 0;
    }

    private LogWriter() {}

    static synchronized LogWriter getInstance() {
        if (instance == null) {
            instance = new LogWriter();
        }
        return instance;
    }

    void init(HLoggerConfig config) {
        this.logDir = config.getLogDir();
        this.retainDays = config.getRetainDays();
        this.maxStorageSizeMB = config.getMaxStorageSize();
        this.logFormat = config.getLogFormat();
        this.bufferSize = config.getBufferSize();
        this.maxLogFileSize = config.getMaxLogFileSize();
        this.buffer = new StringBuilder(bufferSize);

        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        cleanOldLogs();
        enforceStorageLimit();
        startFlushTimer(config.getFlushInterval());
    }

    void write(String level, String tag, String msg, String threadName,
               String caller, Map<String, String> headers) {
        if (buffer == null) return;
        executor.execute(() -> {
            String line;
            if (logFormat == HLoggerConfig.FORMAT_JSON) {
                line = buildJsonLine(level, tag, msg, threadName, caller, headers);
            } else {
                line = buildPlainLine(level, tag, msg, threadName, caller);
            }
            appendToBuffer(line);
        });
    }

    private String buildPlainLine(String level, String tag, String msg, 
                                  String threadName, String caller) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timeFormat.get().format(new Date())).append("] ");
        sb.append("[").append(level).append("/").append(tag).append("] ");
        sb.append("[").append(threadName).append("] ");
        if (caller != null) {
            sb.append("[").append(caller).append("] ");
        }
        sb.append(msg).append("\n");
        return sb.toString();
    }

    private String buildJsonLine(String level, String tag, String msg, 
                                 String threadName, String caller, Map<String, String> headers) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"time\":\"").append(timeFormat.get().format(new Date())).append("\",");
        sb.append("\"level\":\"").append(level).append("\",");
        sb.append("\"tag\":\"").append(escapeJson(tag)).append("\",");
        sb.append("\"thread\":\"").append(escapeJson(threadName)).append("\",");
        if (caller != null) {
            sb.append("\"caller\":\"").append(escapeJson(caller)).append("\",");
        }
        if (headers != null && !headers.isEmpty()) {
            sb.append("\"header\":{");
            boolean first = true;
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                  .append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
            sb.append("},");
        }
        sb.append("\"message\":\"").append(escapeJson(msg)).append("\"");
        sb.append("}\n");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private synchronized void appendToBuffer(String line) {
        buffer.append(line);
        if (buffer.length() >= bufferSize) {
            flushBuffer();
        }
    }

    void flush() {
        executor.execute(this::flushBuffer);
    }

    void flushSync() {
        try {
            executor.submit(() -> {}).get(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        flushBuffer();
    }

    private synchronized void flushBuffer() {
        if (buffer == null || buffer.length() == 0) return;

        String content = buffer.toString();
        buffer.setLength(0);

        File logFile = getLogFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(content);
        } catch (IOException ignored) {}

        enforceStorageLimitSync();
    }

    private File getLogFile() {
        String today = dateFormat.get().format(new Date());
        String baseName = "hlog_" + today;

        if (!baseName.equals(currentFileName)) {
            currentFileName = baseName;
            currentFileIndex = 0;
        }

        File file = new File(logDir, currentFileName +
                (currentFileIndex > 0 ? "_" + currentFileIndex : "") + ".txt");

        if (file.exists() && file.length() >= maxLogFileSize) {
            currentFileIndex++;
            file = new File(logDir, currentFileName + "_" + currentFileIndex + ".txt");
        }

        return file;
    }

    private void startFlushTimer(int intervalMs) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            executor.execute(this::flushIfNeeded);
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void flushIfNeeded() {
        if (buffer != null && buffer.length() > 0) {
            flushBuffer();
        }
    }

    File getLogDir() {
        return logDir;
    }

    File[] getLogFiles() {
        if (logDir == null || !logDir.exists()) {
            return new File[0];
        }
        File[] files = logDir.listFiles((dir, name) -> name.startsWith("hlog_") && name.endsWith(".txt"));
        return files != null ? files : new File[0];
    }

    File[] getCrashFiles() {
        if (logDir == null || !logDir.exists()) {
            return new File[0];
        }
        File[] files = logDir.listFiles((dir, name) -> name.startsWith("crash_") && name.endsWith(".txt"));
        return files != null ? files : new File[0];
    }

    File[] getAnrFiles() {
        if (logDir == null || !logDir.exists()) {
            return new File[0];
        }
        File[] files = logDir.listFiles((dir, name) -> name.startsWith("anr_") && name.endsWith(".txt"));
        return files != null ? files : new File[0];
    }

    File[] getNonFatalFiles() {
        if (logDir == null || !logDir.exists()) {
            return new File[0];
        }
        File[] files = logDir.listFiles((dir, name) -> name.startsWith("nonfatal_") && name.endsWith(".txt"));
        return files != null ? files : new File[0];
    }

    File[] getNativeFiles() {
        if (logDir == null || !logDir.exists()) {
            return new File[0];
        }
        File[] files = logDir.listFiles((dir, name) -> name.startsWith("native_") && name.endsWith(".txt"));
        return files != null ? files : new File[0];
    }

    File[] getXCrashResidualFiles() {
        if (logDir == null) return new File[0];
        File xcrashDir = new File(logDir, "xcrash");
        if (!xcrashDir.exists() || !xcrashDir.isDirectory()) return new File[0];
        File[] files = xcrashDir.listFiles(File::isFile);
        return files != null ? files : new File[0];
    }

    File[] getAllReportFiles() {        java.util.List<File> all = new java.util.ArrayList<>();
        java.util.Collections.addAll(all, getCrashFiles());
        java.util.Collections.addAll(all, getAnrFiles());
        java.util.Collections.addAll(all, getNonFatalFiles());
        java.util.Collections.addAll(all, getNativeFiles());
        return all.toArray(new File[0]);
    }

    private void cleanOldLogs() {
        executor.execute(() -> {
            long cutoff = System.currentTimeMillis() - (long) retainDays * 24 * 60 * 60 * 1000;
            deleteExpired(getLogFiles(), cutoff);
            deleteExpired(getCrashFiles(), cutoff);
            deleteExpired(getAnrFiles(), cutoff);
            deleteExpired(getNonFatalFiles(), cutoff);
            deleteExpired(getNativeFiles(), cutoff);
            deleteExpired(getEventFiles(), cutoff);
            deleteExpired(getXCrashResidualFiles(), cutoff);
        });
    }

    /**
     * 删除保留天数之外的日志文件。
     * @param days 保留最近 N 天的日志，days <= 0 时清空全部
     * @param callback 删除完成回调，传入实际删除的文件数与释放的字节数
     */
    void deleteLogsRetainDays(int days, DeleteCallback callback) {
        executor.execute(() -> {
            long cutoff = days <= 0
                    ? Long.MAX_VALUE
                    : System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;
            int[] count = new int[1];
            long[] bytes = new long[1];
            deleteExpired(getLogFiles(), cutoff, count, bytes);
            deleteExpired(getCrashFiles(), cutoff, count, bytes);
            deleteExpired(getAnrFiles(), cutoff, count, bytes);
            deleteExpired(getNonFatalFiles(), cutoff, count, bytes);
            deleteExpired(getNativeFiles(), cutoff, count, bytes);
            deleteExpired(getEventFiles(), cutoff, count, bytes);
            deleteExpired(getXCrashResidualFiles(), cutoff, count, bytes);
            if (callback != null) {
                callback.onDone(count[0], bytes[0]);
            }
        });
    }

    interface DeleteCallback {
        void onDone(int deletedCount, long deletedBytes);
    }

    private static void deleteExpired(File[] files, long cutoff, int[] count, long[] bytes) {
        if (files == null) return;
        for (File file : files) {
            if (file.lastModified() < cutoff) {
                long size = file.length();
                if (file.delete()) {
                    count[0]++;
                    bytes[0] += size;
                }
            }
        }
    }

    private static void deleteExpired(File[] files, long cutoff) {
        if (files == null) return;
        for (File file : files) {
            if (file.lastModified() < cutoff) {
                file.delete();
            }
        }
    }

    private void enforceStorageLimit() {
        executor.execute(this::enforceStorageLimitSync);
    }

    private void enforceStorageLimitSync() {
        java.util.List<File> all = new java.util.ArrayList<>();
        java.util.Collections.addAll(all, getLogFiles());
        java.util.Collections.addAll(all, getCrashFiles());
        java.util.Collections.addAll(all, getAnrFiles());
        java.util.Collections.addAll(all, getNonFatalFiles());
        java.util.Collections.addAll(all, getNativeFiles());
        java.util.Collections.addAll(all, getEventFiles());
        java.util.Collections.addAll(all, getXCrashResidualFiles());

        long totalSize = 0;
        for (File file : all) {
            totalSize += file.length();
        }

        long maxBytes = (long) maxStorageSizeMB * 1024 * 1024;
        if (totalSize <= maxBytes) return;

        all.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File file : all) {
            if (totalSize <= maxBytes) break;
            totalSize -= file.length();
            file.delete();
        }
    }

    // --- Events ---

    void writeEvent(String category, Map<String, Object> eventData) {
        if (logDir == null || category == null) return;
        executor.execute(() -> {
            String line = buildEventLine(eventData);
            File file = getEventFile(category);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                writer.write(line);
            } catch (IOException ignored) {}
        });
    }

    File getEventDir() {
        if (logDir == null) return null;
        File dir = new File(logDir, "events");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    File[] getEventFiles() {
        File dir = getEventDir();
        if (dir == null || !dir.exists()) return new File[0];
        File[] files = dir.listFiles((d, name) -> name.startsWith("event_") && name.endsWith(".txt"));
        return files != null ? files : new File[0];
    }

    File[] getEventFiles(String category) {
        File dir = getEventDir();
        if (dir == null || !dir.exists() || category == null) return new File[0];
        String prefix = "event_" + category + "_";
        File[] files = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".txt"));
        return files != null ? files : new File[0];
    }

    private File getEventFile(String category) {
        File dir = getEventDir();
        String today = dateFormat.get().format(new Date());
        String baseName = "event_" + category + "_" + today;

        EventFileState state = eventStates.get(category);
        if (state == null) {
            state = new EventFileState();
            eventStates.put(category, state);
        }
        if (!baseName.equals(state.currentFileName)) {
            state.currentFileName = baseName;
            state.currentFileIndex = 0;
        }

        File file = new File(dir, state.currentFileName +
                (state.currentFileIndex > 0 ? "_" + state.currentFileIndex : "") + ".txt");
        if (file.exists() && file.length() >= maxLogFileSize) {
            state.currentFileIndex++;
            file = new File(dir, state.currentFileName + "_" + state.currentFileIndex + ".txt");
        }
        return file;
    }

    private String buildEventLine(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            appendJsonValue(sb, entry.getValue());
            first = false;
        }
        sb.append("}\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append("{");
            boolean first = true;
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) value).entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(String.valueOf(e.getKey()))).append("\":");
                appendJsonValue(sb, e.getValue());
                first = false;
            }
            sb.append("}");
        } else {
            sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
        }
    }
}
