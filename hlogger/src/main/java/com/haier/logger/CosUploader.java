package com.haier.logger;

import android.content.Context;
import android.util.Log;

import com.tencent.cos.xml.CosXmlService;
import com.tencent.cos.xml.CosXmlServiceConfig;
import com.tencent.cos.xml.exception.CosXmlClientException;
import com.tencent.cos.xml.exception.CosXmlServiceException;
import com.tencent.cos.xml.listener.CosXmlResultListener;
import com.tencent.cos.xml.model.CosXmlRequest;
import com.tencent.cos.xml.model.CosXmlResult;
import com.tencent.cos.xml.model.object.PutObjectRequest;
import com.tencent.qcloud.core.auth.ShortTimeCredentialProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CosUploader {

    private static final String TAG = "CosUploader[pid:" + android.os.Process.myPid() + "]";
    private static CosXmlService cosService;

    static void init(HLoggerConfig config) {
        try {
            String region = config.getCosRegion();
            String secretId = config.getCosSecretId();
            String secretKey = config.getCosSecretKey();

            CosXmlServiceConfig serviceConfig = new CosXmlServiceConfig.Builder()
                    .setRegion(region)
                    .isHttps(true)
                    .builder();

            ShortTimeCredentialProvider credentialProvider = new ShortTimeCredentialProvider(
                    secretId, secretKey, 600);

            cosService = new CosXmlService(config.getContext(), serviceConfig, credentialProvider);
            Log.i(TAG, "COS uploader initialized successfully");
        } catch (Exception e) {
            // COS初始化失败不应影响日志功能，记录错误但继续运行
            Log.e(TAG, "Failed to initialize COS uploader: " + e.getMessage());
            cosService = null;
        }
    }

    static void upload(Context context, File zipFile, String clientId) {
        upload(context, zipFile, clientId, null);
    }

    static void upload(Context context, File zipFile, String clientId, LogUploader.UploadCallback callback) {
        uploadInternal(context, zipFile, clientId, "logs", "upload_logs", "hlog_", callback);
    }

    static void uploadEvents(Context context, File zipFile, String clientId, String category,
                             LogUploader.UploadCallback callback) {
        String prefix = (category != null ? category : "all") + "_";
        uploadInternal(context, zipFile, clientId, "events", "upload_events", prefix, callback);
    }

    private static void uploadInternal(Context context, File zipFile, String clientId,
                                       String rootDir, String action, String namePrefix,
                                       LogUploader.UploadCallback callback) {
        if (cosService == null) {
            Log.e(TAG, "COS not initialized");
            if (zipFile != null) zipFile.delete();
            if (callback != null) callback.onResult(action, false, "COS not initialized");
            return;
        }

        if (zipFile == null || !zipFile.exists()) {
            Log.e(TAG, "Zip file null or missing");
            if (callback != null) callback.onResult(action, false, "zip file null or missing");
            return;
        }

        HLoggerConfig config = HLogger.getConfig();
        if (config == null) {
            Log.e(TAG, "HLoggerConfig null (not initialized?)");
            zipFile.delete();
            if (callback != null) callback.onResult(action, false, "config null");
            return;
        }

        String bucket = config.getCosBucket();
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String clinic = config.getClinicCode();
        if (clinic == null || clinic.isEmpty()) clinic = "unassigned";

        String deviceCode = config.getDeviceCode();
        if (deviceCode == null || deviceCode.isEmpty()) deviceCode = "unknown";

        String appIdentifier = context != null ? context.getPackageName() : "unknown";
        String cosPath = rootDir + "/" + clinic + "/" + deviceCode
                + "/" + appIdentifier + "/" + namePrefix + timestamp + ".zip";

        PutObjectRequest request = new PutObjectRequest(bucket, cosPath, zipFile.getAbsolutePath());

        cosService.putObjectAsync(request, new CosXmlResultListener() {
            @Override
            public void onSuccess(CosXmlRequest cosXmlRequest, CosXmlResult cosXmlResult) {
                Log.i(TAG, "Upload success: " + cosPath);
                zipFile.delete();
                if (callback != null) callback.onResult(action, true, cosPath);
            }

            @Override
            public void onFail(CosXmlRequest cosXmlRequest, CosXmlClientException e, CosXmlServiceException e1) {
                // COS SDK 可能传入 e 或 e1 之一为 null，极端情况下两者都可能为 null
                String error;
                if (e != null) {
                    error = e.getMessage();
                } else if (e1 != null) {
                    error = e1.getMessage();
                } else {
                    error = "unknown COS error (both exceptions null)";
                }
                Log.e(TAG, "Upload failed: " + error);

                // 上传失败也删除 zip 包：调用方（LogUploader）在失败时不会重用该 zip，
                // 下次上传会重新打包。不删除会导致缓存目录累积失败 zip（kiosk 设备长期运行）
                zipFile.delete();

                if (callback != null) callback.onResult(action, false, error);
            }
        });
    }
}
