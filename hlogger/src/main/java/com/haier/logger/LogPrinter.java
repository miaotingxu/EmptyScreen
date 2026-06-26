package com.haier.logger;

import android.util.Log;

import java.util.Map;

public class LogPrinter {

    private static final int MAX_LOG_LENGTH = 4000;
    private static final String TOP_BORDER = "┌────────────────────────────────────────────────────────────";
    private static final String MIDDLE_BORDER = "├────────────────────────────────────────────────────────────";
    private static final String BOTTOM_BORDER = "└────────────────────────────────────────────────────────────";
    private static final String SIDE_BORDER = "│ ";

    private final boolean enableBorder;
    private final boolean enableConsole;

    public LogPrinter(boolean enableConsole, boolean enableBorder) {
        this.enableConsole = enableConsole;
        this.enableBorder = enableBorder;
    }

    public void print(int level, String tag, String message, Map<String, String> headers,
                      String threadName, String caller) {
        if (!enableConsole) return;

        // Message already carries its own border (e.g. HLogInterceptor builds a
        // full frame): print as-is to avoid wrapping it in a second border.
        if (message != null && message.startsWith(TOP_BORDER)) {
            printLongMessage(level, tag, message);
            return;
        }

        boolean multiLine = message != null && message.indexOf('\n') >= 0;
        if (enableBorder && multiLine) {
            printWithBorder(level, tag, message, headers, threadName, caller);
        } else {
            printSimple(level, tag, message, threadName, caller);
        }
    }

    private void printWithBorder(int level, String tag, String message,
                                 Map<String, String> headers, String threadName, String caller) {
        StringBuilder sb = new StringBuilder();

        sb.append(TOP_BORDER).append("\n");

        String prefix = buildBorderMeta(threadName, caller);
        if (prefix != null) {
            sb.append(SIDE_BORDER).append(prefix).append("\n");
            sb.append(MIDDLE_BORDER).append("\n");
        }

        String[] lines = message.split("\n");
        for (String line : lines) {
            sb.append(SIDE_BORDER).append(line).append("\n");
        }

        sb.append(BOTTOM_BORDER);

        String output = sb.toString();
        printLongMessage(level, tag, output);
    }

    private String buildBorderMeta(String threadName, String caller) {
        if (threadName == null && caller == null) return null;
        StringBuilder sb = new StringBuilder();
        if (threadName != null) {
            sb.append("Thread: ").append(threadName);
        }
        if (caller != null) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(caller);
        }
        return sb.toString();
    }

    private String buildMetaPrefix(String threadName, String caller) {
        if (threadName == null && caller == null) return null;
        StringBuilder sb = new StringBuilder();
        if (threadName != null) {
            sb.append(threadName);
        }
        if (caller != null) {
            if (sb.length() > 0) sb.append('/');
            sb.append(caller);
        }
        return sb.toString();
    }

    private void printSimple(int level, String tag, String message, String threadName, String caller) {
        String prefix = buildMetaPrefix(threadName, caller);
        String output = prefix != null ? "[" + prefix + "] " + message : message;
        printLongMessage(level, tag, output);
    }

    private void printLongMessage(int level, String tag, String message) {
        if (message.length() <= MAX_LOG_LENGTH) {
            logByLevel(level, tag, message);
            return;
        }

        int parts = (message.length() + MAX_LOG_LENGTH - 1) / MAX_LOG_LENGTH;
        for (int i = 0; i < parts; i++) {
            int start = i * MAX_LOG_LENGTH;
            int end = Math.min(start + MAX_LOG_LENGTH, message.length());
            String part = message.substring(start, end);
            String partTag = tag + " [" + (i + 1) + "/" + parts + "]";
            logByLevel(level, partTag, part);
        }
    }

    private void logByLevel(int level, String tag, String message) {
        switch (level) {
            case 2: Log.v(tag, message); break;
            case 3: Log.d(tag, message); break;
            case 4: Log.i(tag, message); break;
            case 5: Log.w(tag, message); break;
            case 6: Log.e(tag, message); break;
            default: Log.d(tag, message); break;
        }
    }
}
