package com.haier.emptyscreen.utils;

import android.webkit.URLUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

public class UrlValidator {
    
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)" +
            "(([a-zA-Z0-9\\-]+\\.)+[a-zA-Z]{2,}|" +
            "localhost|" +
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})" +
            "(:\\d+)?" +
            "(/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?$",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final String[] BLOCKED_SCHEMES = {
            "file:",
            "javascript:",
            "data:",
            "content:"
    };
    
    private static final String[] DANGEROUS_DOMAINS = {
            "malware",
            "phishing",
            "virus"
    };
    
    public static boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        String trimmedUrl = url.trim();
        
        if (!URLUtil.isNetworkUrl(trimmedUrl)) {
            return false;
        }
        
        for (String scheme : BLOCKED_SCHEMES) {
            if (trimmedUrl.toLowerCase().startsWith(scheme)) {
                LogUtils.w("[UrlValidator] Blocked scheme detected: " + scheme);
                return false;
            }
        }
        
        try {
            URL parsedUrl = new URL(trimmedUrl);
            String protocol = parsedUrl.getProtocol();
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                LogUtils.w("[UrlValidator] Invalid protocol: " + protocol);
                return false;
            }
            
            String host = parsedUrl.getHost();
            if (host == null || host.isEmpty()) {
                LogUtils.w("[UrlValidator] Empty host");
                return false;
            }
            
            String lowerHost = host.toLowerCase();
            for (String dangerous : DANGEROUS_DOMAINS) {
                if (lowerHost.contains(dangerous)) {
                    LogUtils.w("[UrlValidator] Dangerous domain detected: " + host);
                    return false;
                }
            }
            
        } catch (MalformedURLException e) {
            LogUtils.e("[UrlValidator] Malformed URL: " + trimmedUrl + ", " + e.getMessage());
            return false;
        }
        
        return URL_PATTERN.matcher(trimmedUrl).matches();
    }
    
    public static String normalizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        
        String normalized = url.trim();
        
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        
        if (!normalized.contains("/") || normalized.lastIndexOf("/") < normalized.indexOf("://") + 3) {
            normalized = normalized + "/";
        }
        
        return normalized;
    }
    
    public static String getSafeUrl(String url) {
        if (!isValidUrl(url)) {
            return null;
        }
        return normalizeUrl(url);
    }
    
    public static String getValidationError(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "URL不能为空";
        }
        
        String trimmedUrl = url.trim();
        
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return "URL必须以http://或https://开头";
        }
        
        for (String scheme : BLOCKED_SCHEMES) {
            if (trimmedUrl.toLowerCase().startsWith(scheme)) {
                return "不允许使用" + scheme + "协议";
            }
        }
        
        try {
            URL parsedUrl = new URL(trimmedUrl);
            if (parsedUrl.getHost() == null || parsedUrl.getHost().isEmpty()) {
                return "无效的主机名";
            }
        } catch (MalformedURLException e) {
            return "URL格式错误: " + e.getMessage();
        }
        
        if (!URL_PATTERN.matcher(trimmedUrl).matches()) {
            return "URL格式不正确";
        }
        
        return null;
    }
}
