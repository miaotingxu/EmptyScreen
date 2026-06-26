package com.haier.logger;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

final class MainLooperCatcher {

    private static final String TAG = "MainLooperCatcher[pid:" + android.os.Process.myPid() + "]";
    private static volatile boolean installed = false;

    private MainLooperCatcher() {}

    static void install(HLoggerConfig config) {
        if (installed) return;
        installed = true;

        new Handler(Looper.getMainLooper()).post(() -> {
            while (true) {
                try {
                    Looper.loop();
                    return;
                } catch (Throwable throwable) {
                    if (isFatal(throwable)) {
                        Log.e(TAG, "Fatal error in main looper, handing to UEH", throwable);
                        Thread thread = Thread.currentThread();
                        Thread.UncaughtExceptionHandler ueh = thread.getUncaughtExceptionHandler();
                        if (ueh != null) {
                            ueh.uncaughtException(thread, throwable);
                        }
                        return;
                    }
                    try {
                        CrashCatcher.writeNonFatalReport(
                                config, "MainLooper", throwable);
                        LogWriter.getInstance().flush();
                    } catch (Throwable ignored) {}
                    Log.w(TAG, "Recoverable main looper exception, continuing", throwable);
                }
            }
        });
    }

    private static boolean isFatal(Throwable t) {
        return t instanceof Error
                || (t.getMessage() != null && t.getMessage().contains("Looper"));
    }
}
