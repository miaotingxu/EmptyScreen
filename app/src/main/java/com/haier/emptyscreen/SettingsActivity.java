package com.haier.emptyscreen;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.MemoryCleaner;
import com.haier.emptyscreen.utils.NetworkUtils;
import com.haier.emptyscreen.utils.PrefsManager;

public class SettingsActivity extends Activity {

    private static final String TAG = "[SettingsActivity]";

    private EditText mUrlEditText;
    private EditText mBootDelayEditText;
    private EditText mForegroundDelayEditText;
    private EditText mCleanIntervalEditText;
    private Button mSaveButton;
    private Button mSaveTimeSettingsButton;
    private Button mCleanNowButton;
    private ImageButton mSystemSettingsButton;
    private ImageButton mBackButton;

    private SwitchCompat mMemoryCleanEnabledSwitch;
    private SeekBar mThresholdSeekBar;
    private TextView mThresholdValueText;
    private TextView mCleanLogText;

    private TextView mNetworkStatusText;
    private TextView mNetworkTypeText;
    private TextView mNetworkNameText;
    private TextView mIpAddressText;
    private TextView mMemoryPercentText;
    private TextView mTotalMemoryText;
    private TextView mAvailableMemoryText;
    private TextView mUsedMemoryText;
    private TextView mUrlErrorText;

    private PrefsManager mPrefsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mPrefsManager = PrefsManager.getInstance(this);

        initializeViews();
        loadSavedSettings();
        setupClickListeners();
        updateNetworkInfo();
        updateMemoryInfo();
    }

    private void initializeViews() {
        mUrlEditText = findViewById(R.id.et_url);
        mBootDelayEditText = findViewById(R.id.et_boot_delay);
        mForegroundDelayEditText = findViewById(R.id.et_foreground_delay);
        mCleanIntervalEditText = findViewById(R.id.et_clean_interval);
        mSaveButton = findViewById(R.id.btn_save);
        mSaveTimeSettingsButton = findViewById(R.id.btn_save_time_settings);
        mCleanNowButton = findViewById(R.id.btn_clean_now);
        mSystemSettingsButton = findViewById(R.id.btn_system_settings);
        mBackButton = findViewById(R.id.btn_back);

        mMemoryCleanEnabledSwitch = findViewById(R.id.switch_memory_clean_enabled);
        mThresholdSeekBar = findViewById(R.id.seekbar_threshold);
        mThresholdValueText = findViewById(R.id.tv_threshold_value);
        mCleanLogText = findViewById(R.id.tv_clean_log);

        mNetworkStatusText = findViewById(R.id.tv_network_status);
        mNetworkTypeText = findViewById(R.id.tv_network_type);
        mNetworkNameText = findViewById(R.id.tv_network_name);
        mIpAddressText = findViewById(R.id.tv_ip_address);
        mMemoryPercentText = findViewById(R.id.tv_memory_percent);
        mTotalMemoryText = findViewById(R.id.tv_total_memory);
        mAvailableMemoryText = findViewById(R.id.tv_available_memory);
        mUsedMemoryText = findViewById(R.id.tv_used_memory);
        mUrlErrorText = findViewById(R.id.tv_url_error);

        mThresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int threshold = 60 + progress;
                mThresholdValueText.setText(threshold + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void loadSavedSettings() {
        mUrlEditText.setText(mPrefsManager.getUrl());
        mBootDelayEditText.setText(String.valueOf(mPrefsManager.getBootDelaySeconds()));
        mForegroundDelayEditText.setText(String.valueOf(mPrefsManager.getForegroundDelaySeconds()));
        mCleanIntervalEditText.setText(String.valueOf(mPrefsManager.getMemoryCleanInterval()));

        int threshold = mPrefsManager.getMemoryCleanThreshold();
        mThresholdSeekBar.setProgress(threshold - 60);
        mThresholdValueText.setText(threshold + "%");

        mMemoryCleanEnabledSwitch.setChecked(mPrefsManager.isMemoryCleanEnabled());
    }

    private void setupClickListeners() {
        mSaveButton.setOnClickListener(v -> saveUrlSettings());
        mSaveTimeSettingsButton.setOnClickListener(v -> saveTimeSettings());
        mCleanNowButton.setOnClickListener(v -> cleanMemory());
        mSystemSettingsButton.setOnClickListener(v -> openSystemSettings());
        mBackButton.setOnClickListener(v -> finish());

        mMemoryCleanEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefsManager.saveMemoryCleanEnabled(isChecked);
            LogUtils.i(TAG + " Memory clean enabled: " + isChecked);
        });
    }

    private void saveUrlSettings() {
        String url = mUrlEditText.getText().toString().trim();
        if (!NetworkUtils.isValidUrl(url)) {
            mUrlErrorText.setText("URL格式不正确");
            mUrlErrorText.setVisibility(View.VISIBLE);
            return;
        }
        mUrlErrorText.setVisibility(View.GONE);
        mPrefsManager.saveUrl(url);
        Toast.makeText(this, "URL已保存", Toast.LENGTH_SHORT).show();
        LogUtils.i(TAG + " URL saved: " + url);
    }

    private void saveTimeSettings() {
        try {
            int bootDelay = Integer.parseInt(mBootDelayEditText.getText().toString());
            int foregroundDelay = Integer.parseInt(mForegroundDelayEditText.getText().toString());
            int cleanInterval = Integer.parseInt(mCleanIntervalEditText.getText().toString());
            int threshold = 60 + mThresholdSeekBar.getProgress();

            mPrefsManager.saveBootDelaySeconds(bootDelay);
            mPrefsManager.saveForegroundDelaySeconds(foregroundDelay);
            mPrefsManager.saveMemoryCleanInterval(cleanInterval);
            mPrefsManager.saveMemoryCleanThreshold(threshold);

            Toast.makeText(this, "时间设置已保存", Toast.LENGTH_SHORT).show();
            LogUtils.i(TAG + " Time settings saved");
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
            LogUtils.e(TAG + " Failed to save time settings: " + e.getMessage());
        }
    }

    private void updateNetworkInfo() {
        if (NetworkUtils.isNetworkAvailable(this)) {
            mNetworkStatusText.setText("已连接");
            mNetworkStatusText.setTextColor(getColor(R.color.green_500));
        } else {
            mNetworkStatusText.setText("未连接");
            mNetworkStatusText.setTextColor(getColor(R.color.red_500));
        }

        mNetworkTypeText.setText(NetworkUtils.getNetworkType(this));
        mNetworkNameText.setText(NetworkUtils.getNetworkName(this));
        mIpAddressText.setText(NetworkUtils.getIpAddress(this));
    }

    private void updateMemoryInfo() {
        android.app.ActivityManager.MemoryInfo memoryInfo = new android.app.ActivityManager.MemoryInfo();
        android.app.ActivityManager activityManager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memoryInfo);

        long totalMem = memoryInfo.totalMem;
        long availMem = memoryInfo.availMem;
        long usedMem = totalMem - availMem;
        float percent = (float) usedMem / totalMem * 100;

        mMemoryPercentText.setText(String.format("%.1f%%", percent));
        mTotalMemoryText.setText(formatFileSize(totalMem));
        mAvailableMemoryText.setText(formatFileSize(availMem));
        mUsedMemoryText.setText(formatFileSize(usedMem));
    }

    private String formatFileSize(long bytes) {
        return android.text.format.Formatter.formatFileSize(this, bytes);
    }

    private void cleanMemory() {
        Toast.makeText(this, "正在清理内存...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            MemoryCleaner.CleanResult result = MemoryCleaner.cleanMemory(SettingsActivity.this);

            runOnUiThread(() -> {
                String message = String.format("清理完成，释放 %s",
                        formatFileSize(result.freedBytes));
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                mCleanLogText.setText("最近清理: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
                mCleanLogText.setVisibility(View.VISIBLE);
                updateMemoryInfo();
                LogUtils.i(TAG + " Manual memory cleanup completed: " + result.freedBytes + " bytes freed");
            });
        }).start();
    }

    private void openSystemSettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            LogUtils.d(TAG + " Opened system settings");
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show();
            LogUtils.e(TAG + " Failed to open system settings: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNetworkInfo();
        updateMemoryInfo();
    }
}
