package com.haier.logger;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MqttService {

    private static final String TAG = "[MqttService--->][pid:" + android.os.Process.myPid() + "]";
    private static MqttService instance;
    private MqttClient client;
    private Context context;
    private String clientId;
    private String clinicCode;
    private String deviceCode;
    private String appCode;
    private HLoggerConfig config;
    private ScheduledExecutorService statusScheduler;
    private int retryAttempt = 0;
    private static final long[] RETRY_BACKOFF_SEC = {10, 30, 60, 120, 300};

    private MqttService() {
    }

    static synchronized MqttService getInstance() {
        if (instance == null) {
            instance = new MqttService();
        }
        return instance;
    }

    void init(Context context, HLoggerConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.clientId = config.getMqttClientId();
        this.clinicCode = config.getClinicCode();
        this.deviceCode = config.getDeviceCode();
        this.appCode = config.getAppCode();
        startStatusLogger();
        connect();
    }

    private void connect() {
        new Thread(() -> {
            try {
                String broker = config.getMqttBroker();
                File persistDir = new File(context.getFilesDir(), "mqtt-persist");
                if (!persistDir.exists()) persistDir.mkdirs();
                client = new MqttClient(broker, clientId, new MqttDefaultFilePersistence(persistDir.getAbsolutePath()));

                MqttConnectOptions options = buildConnectOptions();

                client.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        HLogger.i(TAG, "MQTT " + (reconnect ? "reconnected" : "connected") + " to " + serverURI);
                        retryAttempt = 0;
                        try {
                            subscribeTopics();
                        } catch (MqttException e) {
                            Log.e(TAG, "Re-subscribe failed", e);
                        }
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        String msg = cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : "unknown";
                        Log.w(TAG, "MQTT connection lost, will auto-reconnect. cause=" + msg, cause);
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        handleMessage(new String(message.getPayload()));
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                    }
                });

                client.connect(options);

            } catch (MqttException e) {
                Log.e(TAG, "MQTT connect failed, scheduling retry", e);
                scheduleRetry();
            }
        }).start();
    }

    private long nextBackoffSec() {
        int idx = Math.min(retryAttempt, RETRY_BACKOFF_SEC.length - 1);
        long delay = RETRY_BACKOFF_SEC[idx];
        retryAttempt++;
        return delay;
    }

    private MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);
        options.setMaxInflight(100);
        String username = config.getMqttUsername();
        String password = config.getMqttPassword();
        if (username != null && !username.isEmpty()) options.setUserName(username);
        if (password != null && !password.isEmpty()) options.setPassword(password.toCharArray());
        return options;
    }

    private void scheduleRetry() {
        if (statusScheduler == null || statusScheduler.isShutdown()) return;
        long delay = nextBackoffSec();
        statusScheduler.schedule(() -> {
            if (client != null && !client.isConnected()) {
                try {
                    client.connect(buildConnectOptions());
                } catch (MqttException e) {
                    Log.e(TAG, "MQTT retry failed, will retry again in " + delay + "s", e);
                    scheduleRetry();
                }
            }
        }, delay, TimeUnit.SECONDS);
    }

    private void subscribeTopics() throws MqttException {
        if (clinicCode != null && !clinicCode.isEmpty()) {
            String clinicTopic = "hlogger/clinic/" + clinicCode + "/cmd";
            client.subscribe(clinicTopic, 1);
            HLogger.i(TAG, "Subscribed (clinicTopic): " + clinicTopic);
        }
        if (deviceCode != null && !deviceCode.isEmpty()) {
            String deviceTopic = "hlogger/device/" + deviceCode + "/cmd";
            client.subscribe(deviceTopic, 1);
            HLogger.i(TAG, "Subscribed (deviceTopic): " + deviceTopic);
        }
        Log.i(TAG, "Identity: clinic=" + (clinicCode != null ? clinicCode : "unset")
                + " device=" + deviceCode + " app=" + appCode);
    }

    public void updateClinicCode(String newClinicCode) {
        if (newClinicCode == null || newClinicCode.equals(clinicCode)) return;
        clinicCode = newClinicCode;
    }

    private void handleMessage(String payload) {
        try {
            JSONObject json = new JSONObject(payload);
            String action = json.optString("action");
            Log.i(TAG, "Received : " + payload);

            String targetApp = json.optString("appCode", null);
            if (targetApp != null && !targetApp.isEmpty() && !targetApp.equals(appCode)) {
                return;
            }

            switch (action) {
                case "upload_logs":
                    Log.i(TAG, "Received upload_logs command");
                    publishProcessing("upload_logs");
                    int days = json.optInt("days", 0);
                    boolean forceReupload = json.optBoolean("forceReupload", false);
                    LogUploader.uploadLogs(context, days, forceReupload, this::publishResult);
                    break;

                case "upload_events":
                    Log.i(TAG, "Received upload_events command");
                    publishProcessing("upload_events");
                    String category = json.optString("category", null);
                    LogUploader.uploadEvents(context, category, this::publishResult);
                    break;

                case "clear_upload_history":
                    Log.i(TAG, "Received clear_upload_history command");
                    publishProcessing("clear_upload_history");
                    new UploadHistory(context).clear();
                    publishResult("clear_upload_history", true, "history cleared");
                    break;

                case "delete_logs":
                    int retainDays = json.optInt("days", 0);
                    Log.i(TAG, "Received delete_logs command, retainDays=" + retainDays);
                    publishProcessing("delete_logs");
                    LogWriter.getInstance().deleteLogsRetainDays(retainDays, (deletedCount, deletedBytes) -> {
                        String detail = "retainDays=" + retainDays
                                + ", deleted=" + deletedCount
                                + ", freedKB=" + (deletedBytes / 1024);
                        publishResult("delete_logs", true, detail);
                        Log.i(TAG, "delete_logs done: " + detail);
                    });
                    break;

                case "flush":
                    publishProcessing("flush");
                    LogWriter.getInstance().flush();
                    Log.i(TAG, "Flush triggered remotely");
                    publishResult("flush", true, null);
                    break;

                case "get_status":
                    publishStatus();
                    break;

                default:
                    Log.w(TAG, "Unknown action: " + action);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse MQTT message", e);
        }
    }

    private void startStatusLogger() {
        statusScheduler = Executors.newSingleThreadScheduledExecutor();
        statusScheduler.scheduleAtFixedRate(() -> {
            boolean connected = client != null && client.isConnected();
            Log.d(TAG, "MQTT status: " + (connected ? "connected" : "disconnected")
                    + " | clinic=" + clinicCode + " device=" + deviceCode + " app=" + appCode);
        }, 10, 10, TimeUnit.SECONDS);
    }

    String getClientId() {
        return clientId;
    }

    boolean isConnected() {
        return client != null && client.isConnected();
    }

    void disconnect() {
        try {
            if (statusScheduler != null) {
                statusScheduler.shutdownNow();
            }
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
        } catch (MqttException ignored) {
        }
    }

    void publishResult(String action, boolean success, String detail) {
        publishResult(action, success, detail, "done");
    }

    private void publishProcessing(String action) {
        publishResult(action, true, "processing", "processing");
    }

    void publishResult(String action, boolean success, String detail, String stage) {
        try {
            if (client == null || !client.isConnected()) return;

            JSONObject json = new JSONObject();
            json.put("action", action);
            json.put("success", success);
            json.put("stage", stage);
            if (!TextUtils.isEmpty(clinicCode)) {
                json.put("clinicCode", clinicCode);
            }
            if (!TextUtils.isEmpty(deviceCode)) {
                json.put("deviceCode", deviceCode);
            }
            if (!TextUtils.isEmpty(appCode)) {
                json.put("appCode", appCode);
            }
//            json.put("timestamp", System.currentTimeMillis());
            if (detail != null) {
                json.put("detail", detail);
            }

            // 回执 topic 带 app 维度（deviceCode + appCode），使同一设备上多个 app 的
            // 回执分流到各自独立的 topic，互不干扰；同时避免 clinicCode 为空时拼出脏 topic。
            String topic = "hlogger/" + deviceCode + "/" + appCode + "/status";
            client.publish(topic, new MqttMessage(json.toString().getBytes()));
        } catch (Exception e) {
            Log.e(TAG, "Failed to publish result", e);
        }
    }

    private void publishStatus() {
        try {
            if (client == null || !client.isConnected()) {
                Log.w(TAG, "MQTT client not connected, skip status publish");
                return;
            }
            File logDir = LogWriter.getInstance().getLogDir();
            File[] logFiles = LogWriter.getInstance().getLogFiles();
            File[] crashFiles = LogWriter.getInstance().getCrashFiles();

            long totalSize = 0;
            for (File f : logFiles) totalSize += f.length();

            JSONObject json = new JSONObject();
            json.put("action", "status");
            if (!TextUtils.isEmpty(clinicCode)) {
                json.put("clinicCode", clinicCode);
            }
            if (!TextUtils.isEmpty(deviceCode)) {
                json.put("deviceCode", deviceCode);
            }
            if (!TextUtils.isEmpty(appCode)) {
                json.put("appCode", appCode);
            }
//            json.put("timestamp", System.currentTimeMillis());
//            json.put("connected", true);
            json.put("logFileCount", logFiles.length);
            json.put("crashFileCount", crashFiles.length);
            json.put("totalLogSizeKB", totalSize / 1024);
            json.put("logDir", logDir != null ? logDir.getAbsolutePath() : "");

            // 回执 topic 带 app 维度，与 publishResult 保持一致。
            String topic = "hlogger/" + deviceCode + "/" + appCode + "/status";
            client.publish(topic, new MqttMessage(json.toString().getBytes()));
            Log.i(TAG, "Status published");
        } catch (Exception e) {
            Log.e(TAG, "Failed to publish status", e);
        }
    }
}
