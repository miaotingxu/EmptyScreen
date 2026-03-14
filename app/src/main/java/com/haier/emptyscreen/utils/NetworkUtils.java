package com.haier.emptyscreen.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Locale;

public class NetworkUtils {
    
    public static final int NETWORK_TYPE_UNKNOWN = 0;
    public static final int NETWORK_TYPE_WIFI = 1;
    public static final int NETWORK_TYPE_MOBILE = 2;
    public static final int NETWORK_TYPE_ETHERNET = 3;
    public static final int NETWORK_TYPE_HOTSPOT = 4;
    
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && 
                   (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
    }
    
    public static int getNetworkType(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return NETWORK_TYPE_UNKNOWN;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return NETWORK_TYPE_UNKNOWN;
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities == null) return NETWORK_TYPE_UNKNOWN;
            
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                if (isWifiApEnabled(context)) {
                    return NETWORK_TYPE_HOTSPOT;
                }
                return NETWORK_TYPE_WIFI;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return NETWORK_TYPE_MOBILE;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return NETWORK_TYPE_ETHERNET;
            }
        } else {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork == null) return NETWORK_TYPE_UNKNOWN;
            
            switch (activeNetwork.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                    return NETWORK_TYPE_WIFI;
                case ConnectivityManager.TYPE_MOBILE:
                    return NETWORK_TYPE_MOBILE;
                case ConnectivityManager.TYPE_ETHERNET:
                    return NETWORK_TYPE_ETHERNET;
            }
        }
        return NETWORK_TYPE_UNKNOWN;
    }
    
    private static boolean isWifiApEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return false;
        
        try {
            Method method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(wifiManager);
        } catch (Exception e) {
            return false;
        }
    }
    
    public static String getNetworkTypeName(Context context) {
        int type = getNetworkType(context);
        switch (type) {
            case NETWORK_TYPE_WIFI:
                return "Wi-Fi";
            case NETWORK_TYPE_MOBILE:
                return "移动网络";
            case NETWORK_TYPE_ETHERNET:
                return "以太网";
            case NETWORK_TYPE_HOTSPOT:
                return "热点";
            default:
                return "未知";
        }
    }
    
    public static String getSSID(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return "";
        
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) return "";
        
        String ssid = wifiInfo.getSSID();
        if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        
        if ("<unknown ssid>".equals(ssid) || ssid == null || ssid.isEmpty()) {
            return "";
        }
        
        return ssid;
    }
    
    public static String getLocalIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                int ipAddress = wifiInfo.getIpAddress();
                if (ipAddress != 0) {
                    return formatIpAddress(ipAddress);
                }
            }
        }
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e("[NetworkUtils] Failed to get IP address: " + e.getMessage());
        }
        
        return "";
    }
    
    private static String formatIpAddress(int ipAddress) {
        return String.format(Locale.getDefault(), "%d.%d.%d.%d",
                (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff),
                (ipAddress >> 24 & 0xff));
    }
    
    public static String getNetworkInfo(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("网络类型: ").append(getNetworkTypeName(context)).append("\n");
        
        String ssid = getSSID(context);
        if (!ssid.isEmpty()) {
            sb.append("网络名称: ").append(ssid).append("\n");
        }
        
        String ip = getLocalIpAddress(context);
        if (!ip.isEmpty()) {
            sb.append("IP地址: ").append(ip);
        }
        
        return sb.toString();
    }
}
