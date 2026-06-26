package com.haier.logger;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import java.lang.reflect.Method;

public class TokenManager {

    private static String clientId;

    static void init(Context context) {
        if (TextUtils.isEmpty(clientId)) {
            clientId = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
    }

    public static String getDeviceNo() {
        String deviceCode = getDeviceCode();
        if (TextUtils.isEmpty(deviceCode)) {
            return clientId;
        }
        return deviceCode;
    }

    private static String getDeviceCode() {
        Object var7 = null;
        String var8;
        Exception var10000;
        label53:
        {
            Class var1;
            Class var9;
            boolean var5;
            try {
                var9 = var1 = Class.forName("android.os.SystemProperties");
            } catch (Exception var20) {
                var10000 = var20;
                var5 = false;
                break label53;
            }

            String var10 = "get";
            byte var10002 = 1;

            Class[] var12;
            try {
                var12 = new Class[var10002];
            } catch (Exception var19) {
                var10000 = var19;
                var5 = false;
                break label53;
            }
            Class[] var10003 = var12;
            byte var10004 = 0;

            Method var11;
            try {
                var10003[var10004] = String.class;
                var11 = var9.getMethod(var10, var12);
            } catch (Exception var18) {
                var10000 = var18;
                var5 = false;
                break label53;
            }

            Class var13 = var1;
            var10002 = 1;

            Object[] var14;
            try {
                var14 = new Object[var10002];
            } catch (Exception var17) {
                var10000 = var17;
                var5 = false;
                break label53;
            }
            Object[] var15 = var14;
            var10004 = 0;
            try {
                var15[var10004] = "ro.serialno";
                var8 = (String) var11.invoke(var13, var14);
                return var8;
            } catch (Exception var16) {
                var10000 = var16;
                var5 = false;
            }
        }
        var10000.printStackTrace();
        var8 = (String) var7;
        return var8;
    }

}
