package com.haier.emptyscreen;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.haier.emptyscreen.utils.FocusUtils;
import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.MemoryUtils;
import com.haier.emptyscreen.utils.NetworkUtils;
import com.haier.emptyscreen.utils.OrientationHelper;
import com.haier.emptyscreen.utils.PrefsManager;
import com.haier.emptyscreen.utils.UrlValidator;

public class SettingsActivity extends AppCompatActivity {

    private static final int MIN_DELAY = 30;
    private static final int MAX_DELAY = 500;

    private ScrollView mScrollView;
    private TextView mTvNetworkStatus;
    private TextView mTvNetworkType;
    private TextView mTvNetworkName;
    private TextView mTvIpAddress;
    private TextView mTvMemoryPercent;
    private ProgressBar mProgressMemory;
    private TextView mTvTotalMemory;
    private TextView mTvAvailableMemory;
    private TextView mTvUsedMemory;
    private EditText mEtUrl;
    private TextView mTvUrlError;
    private Button mBtnSave;
    private EditText mEtBootDelay;
    private EditText mEtForegroundDelay;
    private Button mBtnSaveTimeSettings;
    private ImageButton mBtnBack;
    private ImageButton mBtnSystemSettings;

    private PrefsManager mPrefsManager;
    private int mCurrentOrientation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtils.i("[SettingsActivity] onCreate");
        
        mPrefsManager = PrefsManager.getInstance(this);
        mCurrentOrientation = OrientationHelper.getCurrentOrientation(this);
        
        setContentView(R.layout.activity_settings);
        
        initViews();
        setupTVFocus();
        loadSettings();
        updateNetworkInfo();
        updateMemoryInfo();
        setupListeners();
        
        LogUtils.i("[SettingsActivity] Initial orientation: " + OrientationHelper.getOrientationName(this));
    }
    
    private void setupTVFocus() {
        mBtnBack.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
        });
        
        mBtnSystemSettings.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
        });
        
        mBtnSave.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
        });
        
        mBtnSaveTimeSettings.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
        });
        
        mEtUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                FocusUtils.scrollToFocusedView(mScrollView, v);
            }
        });
        
        mEtBootDelay.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                FocusUtils.scrollToFocusedView(mScrollView, v);
            }
        });
        
        mEtForegroundDelay.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                FocusUtils.scrollToFocusedView(mScrollView, v);
            }
        });
        
        mBtnBack.requestFocus();
    }

    private void initViews() {
        mScrollView = findViewById(R.id.scroll_root);
        mTvNetworkStatus = findViewById(R.id.tv_network_status);
        mTvNetworkType = findViewById(R.id.tv_network_type);
        mTvNetworkName = findViewById(R.id.tv_network_name);
        mTvIpAddress = findViewById(R.id.tv_ip_address);
        mTvMemoryPercent = findViewById(R.id.tv_memory_percent);
        mProgressMemory = findViewById(R.id.progress_memory);
        mTvTotalMemory = findViewById(R.id.tv_total_memory);
        mTvAvailableMemory = findViewById(R.id.tv_available_memory);
        mTvUsedMemory = findViewById(R.id.tv_used_memory);
        mEtUrl = findViewById(R.id.et_url);
        mTvUrlError = findViewById(R.id.tv_url_error);
        mBtnSave = findViewById(R.id.btn_save);
        mEtBootDelay = findViewById(R.id.et_boot_delay);
        mEtForegroundDelay = findViewById(R.id.et_foreground_delay);
        mBtnSaveTimeSettings = findViewById(R.id.btn_save_time_settings);
        mBtnBack = findViewById(R.id.btn_back);
        mBtnSystemSettings = findViewById(R.id.btn_system_settings);
    }

    private void loadSettings() {
        String url = mPrefsManager.getUrl();
        mEtUrl.setText(url);
        
        int bootDelay = mPrefsManager.getBootDelaySeconds();
        mEtBootDelay.setText(String.valueOf(bootDelay));
        
        int foregroundDelay = mPrefsManager.getForegroundDelaySeconds();
        mEtForegroundDelay.setText(String.valueOf(foregroundDelay));
        
        LogUtils.i("[SettingsActivity] Settings loaded - URL: " + url + ", BootDelay: " + bootDelay + ", ForegroundDelay: " + foregroundDelay);
    }

    private void updateNetworkInfo() {
        boolean isConnected = NetworkUtils.isNetworkConnected(this);
        
        if (mTvNetworkStatus != null) {
            if (isConnected) {
                mTvNetworkStatus.setText(R.string.connected);
                mTvNetworkStatus.setTextColor(getColor(R.color.connected_color));
            } else {
                mTvNetworkStatus.setText(R.string.disconnected);
                mTvNetworkStatus.setTextColor(getColor(R.color.disconnected_color));
            }
        }
        
        if (mTvNetworkType != null) {
            String networkType = NetworkUtils.getNetworkTypeName(this);
            mTvNetworkType.setText(networkType);
        }
        
        if (mTvNetworkName != null) {
            String ssid = NetworkUtils.getSSID(this);
            mTvNetworkName.setText(ssid.isEmpty() ? "-" : ssid);
        }
        
        if (mTvIpAddress != null) {
            String ipAddress = NetworkUtils.getLocalIpAddress(this);
            mTvIpAddress.setText(ipAddress.isEmpty() ? "-" : ipAddress);
        }
        
        LogUtils.i("[SettingsActivity] Network info updated - Connected: " + isConnected);
    }

    private void updateMemoryInfo() {
        long totalMemory = MemoryUtils.getTotalMemory(this);
        long availableMemory = MemoryUtils.getAvailableMemory(this);
        long usedMemory = totalMemory - availableMemory;
        float usagePercent = MemoryUtils.getSystemMemoryUsagePercent(this);
        
        if (mTvMemoryPercent != null) {
            mTvMemoryPercent.setText(String.format("%.1f%%", usagePercent));
        }
        if (mProgressMemory != null) {
            mProgressMemory.setProgress((int) usagePercent);
        }
        if (mTvTotalMemory != null) {
            mTvTotalMemory.setText(MemoryUtils.formatSize(totalMemory));
        }
        if (mTvAvailableMemory != null) {
            mTvAvailableMemory.setText(MemoryUtils.formatSize(availableMemory));
        }
        if (mTvUsedMemory != null) {
            mTvUsedMemory.setText(MemoryUtils.formatSize(usedMemory));
        }
        
        LogUtils.i("[SettingsActivity] Memory info updated - Total: " + MemoryUtils.formatSize(totalMemory) 
            + ", Available: " + MemoryUtils.formatSize(availableMemory) 
            + ", Used: " + MemoryUtils.formatSize(usedMemory)
            + ", Percent: " + String.format("%.1f%%", usagePercent));
    }

    private void setupListeners() {
        mBtnBack.setOnClickListener(v -> finish());

        mEtUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                validateUrl(s.toString());
            }
        });

        mEtUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                saveUrl();
                return true;
            }
            return false;
        });

        mBtnSave.setOnClickListener(v -> saveUrl());

        mBtnSaveTimeSettings.setOnClickListener(v -> saveTimeSettings());

        mBtnSystemSettings.setOnClickListener(v -> openSystemSettings());
    }

    private boolean validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            mTvUrlError.setVisibility(View.GONE);
            return false;
        }

        String error = UrlValidator.getValidationError(url.trim());
        if (error != null) {
            mTvUrlError.setText(error);
            mTvUrlError.setVisibility(View.VISIBLE);
            return false;
        } else {
            mTvUrlError.setVisibility(View.GONE);
            return true;
        }
    }

    private void saveUrl() {
        String url = mEtUrl.getText().toString().trim();
        
        if (url.isEmpty()) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!validateUrl(url)) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        String normalizedUrl = UrlValidator.normalizeUrl(url);
        mPrefsManager.saveUrl(normalizedUrl);
        
        Toast.makeText(this, R.string.url_saved, Toast.LENGTH_SHORT).show();
        LogUtils.i("[SettingsActivity] URL saved: " + normalizedUrl);
        
        setResult(RESULT_OK);
        finish();
    }

    private void saveTimeSettings() {
        String bootDelayStr = mEtBootDelay.getText().toString().trim();
        String foregroundDelayStr = mEtForegroundDelay.getText().toString().trim();

        int bootDelay = MIN_DELAY;
        int foregroundDelay = MIN_DELAY;

        try {
            if (!bootDelayStr.isEmpty()) {
                bootDelay = Integer.parseInt(bootDelayStr);
                if (bootDelay < MIN_DELAY) bootDelay = MIN_DELAY;
                if (bootDelay > MAX_DELAY) bootDelay = MAX_DELAY;
            }
        } catch (NumberFormatException e) {
            LogUtils.e("[SettingsActivity] Invalid boot delay value: " + e.getMessage());
        }

        try {
            if (!foregroundDelayStr.isEmpty()) {
                foregroundDelay = Integer.parseInt(foregroundDelayStr);
                if (foregroundDelay < MIN_DELAY) foregroundDelay = MIN_DELAY;
                if (foregroundDelay > MAX_DELAY) foregroundDelay = MAX_DELAY;
            }
        } catch (NumberFormatException e) {
            LogUtils.e("[SettingsActivity] Invalid foreground delay value: " + e.getMessage());
        }

        mPrefsManager.saveBootDelaySeconds(bootDelay);
        mPrefsManager.saveForegroundDelaySeconds(foregroundDelay);

        mEtBootDelay.setText(String.valueOf(bootDelay));
        mEtForegroundDelay.setText(String.valueOf(foregroundDelay));

        Toast.makeText(this, R.string.time_settings_saved, Toast.LENGTH_SHORT).show();
        LogUtils.i("[SettingsActivity] Time settings saved - BootDelay: " + bootDelay + ", ForegroundDelay: " + foregroundDelay);
    }

    private void openSystemSettings() {
        LogUtils.i("[SettingsActivity] Opening system settings");
        
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        try {
            startActivity(intent);
        } catch (Exception e) {
            LogUtils.e("[SettingsActivity] Failed to open system settings: " + e.getMessage());
            
            intent = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (Exception e2) {
                LogUtils.e("[SettingsActivity] Failed to open device info settings: " + e2.getMessage());
                Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNetworkInfo();
        updateMemoryInfo();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
