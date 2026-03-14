package com.haier.emptyscreen.model;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoFile {
    
    private String name;
    private String path;
    private long size;
    private long lastModified;
    private boolean isDirectory;
    
    public VideoFile() {
    }
    
    public VideoFile(File file) {
        this.name = file.getName();
        this.path = file.getAbsolutePath();
        this.size = file.length();
        this.lastModified = file.lastModified();
        this.isDirectory = file.isDirectory();
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public long getSize() {
        return size;
    }
    
    public void setSize(long size) {
        this.size = size;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    
    public boolean isDirectory() {
        return isDirectory;
    }
    
    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }
    
    public String getFormattedSize() {
        if (isDirectory) {
            return "";
        }
        
        double size = this.size;
        if (size < 1024) {
            return String.format(Locale.getDefault(), "%d B", (long) size);
        } else if (size < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", size / 1024);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", size / (1024 * 1024));
        } else {
            return String.format(Locale.getDefault(), "%.1f GB", size / (1024 * 1024 * 1024));
        }
    }
    
    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(lastModified));
    }
    
    public String getExtension() {
        if (name == null || name.isEmpty()) {
            return "";
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1).toUpperCase();
        }
        return "";
    }
}
