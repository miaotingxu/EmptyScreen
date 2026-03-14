package com.haier.emptyscreen.utils;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StorageUtils {

    public static class StorageDevice {
        public String name;
        public String path;
        public String type;
        public long totalSpace;
        public long availableSpace;
        public boolean isRemovable;
        public boolean isMounted;

        public String getFormattedTotalSpace(Context context) {
            return Formatter.formatFileSize(context, totalSpace);
        }

        public String getFormattedAvailableSpace(Context context) {
            return Formatter.formatFileSize(context, availableSpace);
        }

        public float getUsagePercent() {
            if (totalSpace == 0) return 0;
            return ((float) (totalSpace - availableSpace) / totalSpace) * 100;
        }
    }

    public static List<StorageDevice> getStorageDevices(Context context) {
        List<StorageDevice> devices = new ArrayList<>();

        StorageDevice internalStorage = getInternalStorage(context);
        if (internalStorage != null) {
            devices.add(internalStorage);
        }

        List<StorageDevice> externalStorages = getExternalStorages(context);
        devices.addAll(externalStorages);

        return devices;
    }

    private static StorageDevice getInternalStorage(Context context) {
        File internalDir = Environment.getDataDirectory();
        if (internalDir == null || !internalDir.exists()) {
            return null;
        }

        StorageDevice device = new StorageDevice();
        device.name = "内置存储";
        device.path = internalDir.getAbsolutePath();
        device.type = "internal";
        device.isRemovable = false;
        device.isMounted = true;

        try {
            StatFs stat = new StatFs(internalDir.getPath());
            device.totalSpace = stat.getTotalBytes();
            device.availableSpace = stat.getAvailableBytes();
        } catch (Exception e) {
            LogUtils.e("[StorageUtils] Failed to get internal storage stats: " + e.getMessage());
            device.totalSpace = internalDir.getTotalSpace();
            device.availableSpace = internalDir.getUsableSpace();
        }

        return device;
    }

    private static List<StorageDevice> getExternalStorages(Context context) {
        List<StorageDevice> devices = new ArrayList<>();

        File[] externalDirs = context.getExternalFilesDirs(null);
        if (externalDirs != null) {
            for (File dir : externalDirs) {
                if (dir != null) {
                    StorageDevice device = new StorageDevice();
                    
                    String path = dir.getAbsolutePath();
                    if (path.contains("/Android/data/")) {
                        int idx = path.indexOf("/Android/data/");
                        if (idx > 0) {
                            path = path.substring(0, idx);
                        }
                    }
                    
                    device.path = path;
                    device.isMounted = true;
                    
                    String mountPoint = new File(path).getName();
                    if (mountPoint.isEmpty() || mountPoint.equals("0") || mountPoint.equals("emulated")) {
                        continue;
                    }
                    
                    device.name = getStorageDisplayName(path);
                    device.type = "external";
                    device.isRemovable = true;

                    try {
                        File storageFile = new File(path);
                        if (storageFile.exists()) {
                            StatFs stat = new StatFs(path);
                            device.totalSpace = stat.getTotalBytes();
                            device.availableSpace = stat.getAvailableBytes();
                            devices.add(device);
                        }
                    } catch (Exception e) {
                        LogUtils.e("[StorageUtils] Failed to get external storage stats: " + path + ", " + e.getMessage());
                    }
                }
            }
        }

        return devices;
    }

    private static String getStorageDisplayName(String path) {
        if (path == null || path.isEmpty()) {
            return "外部存储";
        }

        String name = new File(path).getName();
        
        if (name.toLowerCase().contains("usb") || name.toLowerCase().contains("udisk")) {
            return "U盘 (" + name + ")";
        }
        if (name.toLowerCase().contains("sd") || name.toLowerCase().contains("extsd")) {
            return "SD卡 (" + name + ")";
        }
        if (name.toLowerCase().contains("storage")) {
            return "外部存储 (" + name + ")";
        }
        
        return "外部存储 (" + name + ")";
    }

    public static boolean isStorageReadable(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.canRead();
    }

    public static List<File> getVideoFiles(File directory) {
        List<File> videoFiles = new ArrayList<>();
        
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return videoFiles;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return videoFiles;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                videoFiles.addAll(getVideoFiles(file));
            } else if (isVideoFile(file.getName())) {
                videoFiles.add(file);
            }
        }

        return videoFiles;
    }

    public static boolean isVideoFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }

        String extension = getFileExtension(fileName).toLowerCase();
        String[] videoExtensions = {".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm", ".m4v", ".3gp", ".ts", ".m2ts"};
        
        for (String ext : videoExtensions) {
            if (extension.equals(ext)) {
                return true;
            }
        }
        
        return false;
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot);
        }
        return "";
    }

    public static String formatFileSize(Context context, long bytes) {
        return Formatter.formatFileSize(context, bytes);
    }
}
