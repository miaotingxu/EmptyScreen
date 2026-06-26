package com.haier.logger;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LogUploader {

    private static final String TAG = "LogUploader[pid:" + android.os.Process.myPid() + "]";
    private static final long STALE_ZIP_TTL_MS = 24 * 60 * 60 * 1000L;
    
    // 批量上传限制
    private static final int MAX_FILES_PER_BATCH = 10;       // 每批最多10个文件
    private static final long MAX_BYTES_PER_BATCH = 5 * 1024 * 1024;  // 每批最大5MB
    private static final long MAX_SINGLE_FILE_SIZE = 10 * 1024 * 1024;  // 单个文件最大10MB

    public interface UploadCallback {
        void onResult(String action, boolean success, String detail);
    }

    static void cleanStaleUploadZips(Context context) {
        File cache = context.getCacheDir();
        if (cache == null || !cache.exists()) return;
        File[] files = cache.listFiles((dir, name) ->
                name.startsWith("hlogs_upload_") && name.endsWith(".zip"));
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - STALE_ZIP_TTL_MS;
        int removed = 0;
        for (File f : files) {
            if (f.lastModified() < cutoff && f.delete()) removed++;
        }
        if (removed > 0) Log.i(TAG, "Removed " + removed + " stale upload zips");
    }

    public static void uploadLogs(Context context) {
        uploadLogs(context, 0, false, null);
    }

    public static void uploadEvents(Context context, String category, UploadCallback callback) {
        LogWriter.getInstance().flush();

        File[] eventFiles = (category != null && !category.isEmpty())
                ? LogWriter.getInstance().getEventFiles(category)
                : LogWriter.getInstance().getEventFiles();

        if (eventFiles.length == 0) {
            Log.w(TAG, "No event files to upload");
            if (callback != null) callback.onResult("upload_events", false, "no files");
            return;
        }

        List<File> filesToZip = new ArrayList<>(Arrays.asList(eventFiles));
        List<File> tempFiles = new ArrayList<>();
        File deviceInfoFile = writeDeviceInfoFile(context, 0, false);
        if (deviceInfoFile != null) {
            filesToZip.add(deviceInfoFile);
            tempFiles.add(deviceInfoFile);
        }

        File zipFile = zipFiles(context, filesToZip);
        if (zipFile == null) {
            Log.e(TAG, "Failed to create event zip file");
            cleanupTempFiles(tempFiles);
            if (callback != null) callback.onResult("upload_events", false, "zip failed");
            return;
        }

        String clientId = MqttService.getInstance().getClientId();
        CosUploader.uploadEvents(context, zipFile, clientId, category, (action, success, detail) -> {
            cleanupTempFiles(tempFiles);
            if (callback != null) callback.onResult(action, success, detail);
        });
    }

    public static void uploadLogs(Context context, int days, boolean forceReupload, UploadCallback callback) {
        LogWriter.getInstance().flush();

        File[] logFiles = getFilteredLogFiles(days);
        File[] crashFiles = LogWriter.getInstance().getCrashFiles();
        File[] anrFiles = LogWriter.getInstance().getAnrFiles();
        File[] nonFatalFiles = LogWriter.getInstance().getNonFatalFiles();
        File[] nativeFiles = LogWriter.getInstance().getNativeFiles();
        File[] eventFiles = LogWriter.getInstance().getEventFiles();

        int reportCount = crashFiles.length + anrFiles.length + nonFatalFiles.length + nativeFiles.length;

        if (logFiles.length == 0 && reportCount == 0 && eventFiles.length == 0) {
            Log.w(TAG, "No log files to upload");
            if (callback != null) callback.onResult("upload_logs", false, "no files");
            return;
        }

        UploadHistory history = new UploadHistory(context);
        List<File> filesToUpload = new ArrayList<>();

        if (forceReupload) {
            filesToUpload.addAll(Arrays.asList(logFiles));
        } else {
            for (File file : logFiles) {
                if (history.shouldUpload(file)) {
                    filesToUpload.add(file);
                } else {
                    Log.d(TAG, "Skip already uploaded: " + file.getName());
                }
            }
        }

        addIfShouldUpload(filesToUpload, crashFiles,    history, forceReupload);
        addIfShouldUpload(filesToUpload, anrFiles,      history, forceReupload);
        addIfShouldUpload(filesToUpload, nonFatalFiles, history, forceReupload);
        addIfShouldUpload(filesToUpload, nativeFiles,   history, forceReupload);
        addIfShouldUpload(filesToUpload, eventFiles,    history, forceReupload);

        if (filesToUpload.isEmpty()) {
            Log.i(TAG, "All files already uploaded, nothing to do");
            if (callback != null) callback.onResult("upload_logs", true, "all files already uploaded");
            return;
        }

        File deviceInfoFile = writeDeviceInfoFile(context, days, forceReupload);
        List<File> tempFiles = new ArrayList<>();
        if (deviceInfoFile != null) {
            tempFiles.add(deviceInfoFile);
        }

        // 分批上传
        List<List<File>> batches = partitionFiles(filesToUpload);
        Log.i(TAG, "Uploading " + filesToUpload.size() + " files in " + batches.size() + " batches");

        uploadBatches(context, batches, history, tempFiles, callback, 0);
    }

    private static void addIfShouldUpload(List<File> dest, File[] files,
                                           UploadHistory history, boolean forceReupload) {
        for (File file : files) {
            if (forceReupload || history.shouldUpload(file)) {
                dest.add(file);
            } else {
                Log.d(TAG, "Skip already uploaded: " + file.getName());
            }
        }
    }

    private static List<List<File>> partitionFiles(List<File> files) {
        List<List<File>> batches = new ArrayList<>();
        List<File> currentBatch = new ArrayList<>();
        long currentSize = 0;

        for (File file : files) {
            long fileSize = file.length();

            // 检查单个文件是否超过最大限制
            if (fileSize > MAX_SINGLE_FILE_SIZE) {
                Log.w(TAG, "File too large, skipping: " + file.getName() + 
                      " (" + formatFileSize(fileSize) + " > " + formatFileSize(MAX_SINGLE_FILE_SIZE) + ")");
                continue;
            }

            // 检查是否需要新建批次
            if (!currentBatch.isEmpty() && 
                (currentBatch.size() >= MAX_FILES_PER_BATCH || 
                 currentSize + fileSize > MAX_BYTES_PER_BATCH)) {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>();
                currentSize = 0;
            }

            currentBatch.add(file);
            currentSize += fileSize;
        }

        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
    }

    private static void uploadBatches(Context context, List<List<File>> batches, 
                                      UploadHistory history, List<File> tempFiles,
                                      UploadCallback callback, int batchIndex) {
        if (batchIndex >= batches.size()) {
            cleanupTempFiles(tempFiles);
            if (callback != null) {
                callback.onResult("upload_logs", true, "all " + batches.size() + " batches uploaded");
            }
            return;
        }

        List<File> batch = batches.get(batchIndex);
        Log.i(TAG, "Uploading batch " + (batchIndex + 1) + "/" + batches.size() + 
              ", " + batch.size() + " files");

        // 添加设备信息文件到每批
        List<File> filesToZip = new ArrayList<>(batch);
        if (!tempFiles.isEmpty()) {
            filesToZip.add(tempFiles.get(0));
        }

        File zipFile = zipFiles(context, filesToZip);
        if (zipFile == null) {
            Log.e(TAG, "Failed to create zip file for batch " + batchIndex);
            // 继续下一批
            uploadBatches(context, batches, history, tempFiles, callback, batchIndex + 1);
            return;
        }

        String clientId = MqttService.getInstance().getClientId();
        CosUploader.upload(context, zipFile, clientId, (action, success, detail) -> {
            if (success) {
                for (File file : batch) {
                    history.markUploaded(file);
                }
                Log.i(TAG, "Batch " + (batchIndex + 1) + " uploaded successfully");
            } else {
                Log.e(TAG, "Batch " + (batchIndex + 1) + " upload failed: " + detail);
            }
            // 继续下一批
            uploadBatches(context, batches, history, tempFiles, callback, batchIndex + 1);
        });
    }

    private static File[] getFilteredLogFiles(int days) {
        File[] allFiles = LogWriter.getInstance().getLogFiles();
        if (days <= 0) return allFiles;

        long cutoff = System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;
        List<File> filtered = new ArrayList<>();
        for (File file : allFiles) {
            if (file.lastModified() >= cutoff) {
                filtered.add(file);
            }
        }
        return filtered.toArray(new File[0]);
    }

    private static File writeDeviceInfoFile(Context context, int days, boolean forceReupload) {
        try {
            File file = new File(context.getCacheDir(), "device_info.txt");
            String info = DeviceInfo.collect(context);

            StringBuilder sb = new StringBuilder(info);
            sb.append("\n--- Upload Parameters ---\n");
            if (days > 0) sb.append("Days: ").append(days).append("\n");
            sb.append("ForceReupload: ").append(forceReupload).append("\n");

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(sb.toString().getBytes());
            }
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    private static File zipFiles(Context context, List<File> files) {
        String name = "hlogs_upload_" + System.currentTimeMillis() + "_" + (System.nanoTime() & 0xFFFFFF) + ".zip";
        File zipFile = new File(context.getCacheDir(), name);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            byte[] buffer = new byte[4096];
            for (File file : files) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    zos.putNextEntry(new ZipEntry(file.getName()));
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
            return zipFile;
        } catch (IOException e) {
            Log.e(TAG, "Zip failed", e);
            return null;
        }
    }

    private static void cleanupTempFiles(List<File> files) {
        for (File file : files) {
            if (file.exists()) file.delete();
        }
    }
}
