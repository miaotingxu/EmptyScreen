package com.haier.emptyscreen.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class NetworkUtils {

    public static boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected();
            }
        } catch (Exception e) {
            LogUtils.e("[NetworkUtils] Failed to check network: " + e.getMessage());
        }
        return false;
    }

    public static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }

    public static String getNetworkType(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                if (networkInfo != null && networkInfo.isConnected()) {
                    int type = networkInfo.getType();
                    if (type == ConnectivityManager.TYPE_WIFI) {
                        return "Wi-Fi";
                    } else if (type == ConnectivityManager.TYPE_MOBILE) {
                        return "移动网络";
                    } else if (type == ConnectivityManager.TYPE_ETHERNET) {
                        return "以太网";
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e("[NetworkUtils] Failed to get network type: " + e.getMessage());
        }
        return "未知";
    }

    public static String getNetworkName(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    String ssid = wifiInfo.getSSID();
                    if (ssid != null && !ssid.contains("unknown")) {
                        return ssid.replace("\"", "");
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e("[NetworkUtils] Failed to get network name: " + e.getMessage());
        }
        return "未知";
    }

    public static String getIpAddress(Context context) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        if (sAddr != null && sAddr.indexOf(':') < 0) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e("[NetworkUtils] Failed to get IP address: " + e.getMessage());
        }
        return "未知";
    }
}
