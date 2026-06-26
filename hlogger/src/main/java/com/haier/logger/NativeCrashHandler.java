package com.haier.logger;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class NativeCrashHandler {

    private static final String TAG = "NativeCrash[pid:" + android.os.Process.myPid() + "]";
    private static volatile boolean installed = false;

    private NativeCrashHandler() {}

    static void install(Context context, HLoggerConfig config) {
        if (installed) return;
        if (!isXCrashAvailable()) {
            Log.i(TAG, "xCrash not on classpath, skip native crash capture");
            return;
        }
        try {
            Class<?> xCrash = Class.forName("xcrash.XCrash");
            Class<?> params = Class.forName("xcrash.XCrash$InitParameters");
            Class<?> nativeCb = Class.forName("xcrash.ICrashCallback");

            Object p = params.getDeclaredConstructor().newInstance();

            File logDir = config.getLogDir();
            File xcrashDir = new File(logDir, "xcrash");
            if (!xcrashDir.exists()) xcrashDir.mkdirs();
            params.getMethod("setLogDir", String.class).invoke(p, xcrashDir.getAbsolutePath());

            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    NativeCrashHandler.class.getClassLoader(),
                    new Class[]{nativeCb},
                    (proxy, method, args) -> {
                        if ("onCrash".equals(method.getName()) && args != null && args.length >= 2) {
                            handleNativeCrash(config, (String) args[0], (String) args[1]);
                        }
                        return null;
                    });

            try {
                params.getMethod("setJavaCallback", nativeCb).invoke(p, callback);
            } catch (NoSuchMethodException ignored) {}
            try {
                params.getMethod("setNativeCallback", nativeCb).invoke(p, callback);
            } catch (NoSuchMethodException ignored) {}
            try {
                params.getMethod("setAnrCallback", nativeCb).invoke(p, callback);
            } catch (NoSuchMethodException ignored) {}

            try {
                params.getMethod("setJavaRethrow", boolean.class).invoke(p, false);
            } catch (NoSuchMethodException ignored) {}

            Method initMethod = xCrash.getMethod("init", Context.class, params);
            initMethod.invoke(null, context, p);

            installed = true;
            Log.i(TAG, "xCrash installed, native crash capture enabled");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to install xCrash: " + t.getMessage());
        }
    }

    private static boolean isXCrashAvailable() {
        try {
            Class.forName("xcrash.XCrash");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void handleNativeCrash(HLoggerConfig config, String originalLogPath, String emergency) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss_SSS", Locale.US).format(new Date());
            String suffix = "_" + (System.nanoTime() & 0xFFFFFF);
            File target = new File(config.getLogDir(), "native_" + timestamp + suffix + ".txt");

            try (FileWriter w = new FileWriter(target)) {
                w.write("====== NATIVE CRASH REPORT ======\n");
                w.write("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + "\n");
                w.write("Source: " + (originalLogPath != null ? originalLogPath : "(xCrash emergency)") + "\n\n");

                if (originalLogPath != null) {
                    File src = new File(originalLogPath);
                    if (src.exists()) {
                        try (BufferedReader r = new BufferedReader(new FileReader(src))) {
                            String line;
                            while ((line = r.readLine()) != null) {
                                w.write(line);
                                w.write("\n");
                            }
                        }
                        src.delete();
                    }
                }
                if (emergency != null && !emergency.isEmpty()) {
                    w.write("\n====== EMERGENCY BUFFER ======\n");
                    w.write(emergency);
                }
            }

            HLogger.CrashListener l = getCrashListener();
            if (l != null) {
                try {
                    l.onCrash(Thread.currentThread(), null, target.getAbsolutePath(), true);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Log.w(TAG, "handleNativeCrash failed: " + t.getMessage());
        }
    }

    private static HLogger.CrashListener getCrashListener() {
        try {
            java.lang.reflect.Field f = CrashCatcher.class.getDeclaredField("externalListener");
            f.setAccessible(true);
            return (HLogger.CrashListener) f.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
