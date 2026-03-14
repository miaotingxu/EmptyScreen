package com.haier.emptyscreen;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.haier.emptyscreen.adapter.FileBrowserAdapter;
import com.haier.emptyscreen.adapter.StorageDeviceAdapter;
import com.haier.emptyscreen.model.VideoFile;
import com.haier.emptyscreen.service.ForegroundService;
import com.haier.emptyscreen.utils.FocusUtils;
import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.MemoryUtils;
import com.haier.emptyscreen.utils.NetworkUtils;
import com.haier.emptyscreen.utils.PrefsManager;
import com.haier.emptyscreen.utils.StorageUtils;
import com.haier.emptyscreen.utils.UrlValidator;
import com.haier.emptyscreen.webview.CustomWebViewClient;
import com.haier.emptyscreen.webview.WebViewPerformanceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements CustomWebViewClient.WebViewErrorCallback {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 1002;
    
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    };

    private WebView mWebView;
    private ProgressBar mProgressBar;
    private TextView mTvMemoryInfo;
    private ImageButton mBtnSettings;
    private LinearLayout mErrorLayout;
    private TextView mTvErrorMessage;
    private Button mBtnRetry;
    
    private PrefsManager mPrefsManager;
    private Handler mMemoryHandler;
    private Runnable mMemoryRunnable;
    private CustomWebViewClient mWebViewClient;
    
    private boolean mIsResumed = false;
    
    private Dialog mStorageDialog;
    private Dialog mFileBrowserDialog;
    private StorageDeviceAdapter mStorageAdapter;
    private FileBrowserAdapter mFileAdapter;
    private ExecutorService mExecutor;
    
    private File mCurrentDirectory;
    private List<File> mDirectoryStack = new ArrayList<>();
    private String mSelectedVideoPath;
    private int mPlayMode = VideoPlayerActivity.PLAY_MODE_LOOP_SINGLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtils.i("[MainActivity] onCreate");
        
        mPrefsManager = PrefsManager.getInstance(this);
        mExecutor = Executors.newSingleThreadExecutor();
        
        setupFullscreenMode();
        setContentView(R.layout.activity_main);
        
        initViews();
        setupWebView();
        setupMemoryMonitor();
        checkAndRequestPermissions();
        startForegroundService();
        
        loadUrl();
    }
    
    private void startForegroundService() {
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        LogUtils.i("[MainActivity] ForegroundService started");
    }
    
    private void setupFullscreenMode() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void initViews() {
        mWebView = findViewById(R.id.webview);
        mProgressBar = findViewById(R.id.progress_bar);
        mTvMemoryInfo = findViewById(R.id.tv_memory_info);
        mBtnSettings = findViewById(R.id.btn_settings);
        mErrorLayout = findViewById(R.id.error_layout);
        mTvErrorMessage = findViewById(R.id.tv_error_message);
        mBtnRetry = findViewById(R.id.btn_retry);
        
        mBtnSettings.setOnClickListener(v -> {
            LogUtils.i("[MainActivity] Settings button clicked");
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebViewPerformanceManager.configureForComplexPage(mWebView, this);
        
        mWebViewClient = new CustomWebViewClient(this, mProgressBar, mErrorLayout, 
                mTvErrorMessage, mBtnRetry, this);
        mWebView.setWebViewClient(mWebViewClient);
        
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (mProgressBar != null) {
                    mProgressBar.setProgress(newProgress);
                    if (newProgress == 100) {
                        mProgressBar.setVisibility(View.GONE);
                    }
                }
            }
        });
        
        LogUtils.i("[MainActivity] WebView configured with performance optimizations");
    }
    long totalMemory;
    private void setupMemoryMonitor() {
         totalMemory = MemoryUtils.getTotalMemory(this);
        long availableMemory = MemoryUtils.getAvailableMemory(this);
        long usedMemory = totalMemory - availableMemory;
        float usagePercent = MemoryUtils.getSystemMemoryUsagePercent(this);
        LogUtils.i("[SettingsActivity] Memory info updated - Total: " + MemoryUtils.formatSize(totalMemory)
                + ", Available: " + MemoryUtils.formatSize(availableMemory)
                + ", Used: " + MemoryUtils.formatSize(usedMemory)
                + ", Percent: " + String.format("%.1f%%", usagePercent));
        mMemoryHandler = new Handler(Looper.getMainLooper());
        mMemoryRunnable = new Runnable() {
            @Override
            public void run() {
                updateMemoryInfo();
                mMemoryHandler.postDelayed(this, 1000);
            }
        };
    }

    private void updateMemoryInfo() {
        float usagePercent = MemoryUtils.getSystemMemoryUsagePercent(this);
        String memoryPercent = MemoryUtils.getMemoryUsagePercent(this);
        mTvMemoryInfo.setText(String.format("%.1f%%", usagePercent));
    }

    private void loadUrl() {
        String url = mPrefsManager.getUrl();
        LogUtils.i("[MainActivity] Loading URL: " + url);
        
        if (!NetworkUtils.isNetworkConnected(this)) {
            showError(getString(R.string.no_network));
            return;
        }
        
        String safeUrl = UrlValidator.getSafeUrl(url);
        if (safeUrl != null) {
            mWebView.loadUrl(safeUrl);
        } else {
            String error = UrlValidator.getValidationError(url);
            showError(error != null ? error : getString(R.string.invalid_url));
            LogUtils.e("[MainActivity] Invalid URL: " + url);
        }
    }

    @Override
    public void onRetry() {
        LogUtils.i("[MainActivity] Retrying to load URL");
        loadUrl();
    }

    private void showError(String message) {
        mErrorLayout.setVisibility(View.VISIBLE);
        mTvErrorMessage.setText(message);
        mWebView.setVisibility(View.GONE);
    }

    private void hideError() {
        mErrorLayout.setVisibility(View.GONE);
        mWebView.setVisibility(View.VISIBLE);
    }

    private void checkAndRequestPermissions() {
        boolean needRequest = false;
        
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        
        if (needRequest) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, STORAGE_PERMISSION_REQUEST_CODE);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, STORAGE_PERMISSION_REQUEST_CODE);
            }
        } else {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    LogUtils.i("[MainActivity] Permission granted: " + permissions[i]);
                } else {
                    LogUtils.w("[MainActivity] Permission denied: " + permissions[i]);
                }
            }
        } else if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showStorageSelectionDialog();
            } else {
                Toast.makeText(this, R.string.access_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                showStorageSelectionDialog();
            } else {
                Toast.makeText(this, R.string.access_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtils.d("[MainActivity] onKeyDown: " + keyCode);
        
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                showStorageSelectionDialog();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (mWebView != null && mWebView.canGoBack()) {
                    mWebView.goBack();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                showStorageSelectionDialog();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void showStorageSelectionDialog() {
        if (mStorageDialog != null && mStorageDialog.isShowing()) {
            mStorageDialog.dismiss();
        }
        
        mStorageDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        mStorageDialog.setContentView(R.layout.dialog_storage_selection);
        mStorageDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        ImageButton btnClose = mStorageDialog.findViewById(R.id.btn_close);
        RecyclerView rvStorageDevices = mStorageDialog.findViewById(R.id.rv_storage_devices);
        ProgressBar progressLoading = mStorageDialog.findViewById(R.id.progress_loading);
        TextView tvEmptyHint = mStorageDialog.findViewById(R.id.tv_empty_hint);
        RadioGroup rgVideoSource = mStorageDialog.findViewById(R.id.rg_video_source);
        RadioButton rbLocal = mStorageDialog.findViewById(R.id.rb_local);
        RadioButton rbNetwork = mStorageDialog.findViewById(R.id.rb_network);
        EditText etNetworkUrl = mStorageDialog.findViewById(R.id.et_network_url);
        Button btnPlayNetwork = mStorageDialog.findViewById(R.id.btn_play_network);
        LinearLayout layoutStorageDevices = mStorageDialog.findViewById(R.id.layout_storage_devices);
        
        rvStorageDevices.setLayoutManager(new LinearLayoutManager(this));
        mStorageAdapter = new StorageDeviceAdapter(this);
        rvStorageDevices.setAdapter(mStorageAdapter);
        
        mStorageAdapter.setOnDeviceClickListener((device, position) -> {
            mStorageAdapter.setSelectedPosition(position);
            mStorageDialog.dismiss();
            showFileBrowserDialog(device.path);
        });
        
        rgVideoSource.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_local) {
                etNetworkUrl.setVisibility(View.GONE);
                btnPlayNetwork.setVisibility(View.GONE);
                layoutStorageDevices.setVisibility(View.VISIBLE);
            } else {
                etNetworkUrl.setVisibility(View.VISIBLE);
                btnPlayNetwork.setVisibility(View.VISIBLE);
                layoutStorageDevices.setVisibility(View.GONE);
            }
        });
        
        btnPlayNetwork.setOnClickListener(v -> {
            String url = etNetworkUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, R.string.enter_network_url, Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("rtsp://")) {
                url = "http://" + url;
            }
            
            mStorageDialog.dismiss();
            playNetworkVideo(url);
        });
        
        btnClose.setOnClickListener(v -> mStorageDialog.dismiss());
        
        btnClose.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
        });
        
        rbLocal.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
        });
        
        rbNetwork.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
        });
        
        btnPlayNetwork.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
        });
        
        mStorageDialog.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dialog.dismiss();
                return true;
            }
            return false;
        });
        
        mStorageDialog.show();
        
        btnClose.requestFocus();
        
        loadStorageDevices(rvStorageDevices, progressLoading, tvEmptyHint);
    }

    private void loadStorageDevices(RecyclerView recyclerView, ProgressBar progressBar, TextView tvEmptyHint) {
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyHint.setVisibility(View.GONE);
        
        mExecutor.execute(() -> {
            List<StorageUtils.StorageDevice> devices = StorageUtils.getStorageDevices(this);
            
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                
                if (devices.isEmpty()) {
                    tvEmptyHint.setVisibility(View.VISIBLE);
                } else {
                    mStorageAdapter.setDevices(devices);
                }
            });
        });
    }

    private void showFileBrowserDialog(String storagePath) {
        if (mFileBrowserDialog != null && mFileBrowserDialog.isShowing()) {
            mFileBrowserDialog.dismiss();
        }
        
        mFileBrowserDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        mFileBrowserDialog.setContentView(R.layout.dialog_file_browser);
        mFileBrowserDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        ImageButton btnBack = mFileBrowserDialog.findViewById(R.id.btn_back);
        TextView tvCurrentPath = mFileBrowserDialog.findViewById(R.id.tv_current_path);
        RecyclerView rvFiles = mFileBrowserDialog.findViewById(R.id.rv_files);
        ProgressBar progressLoading = mFileBrowserDialog.findViewById(R.id.progress_loading);
        TextView tvEmptyHint = mFileBrowserDialog.findViewById(R.id.tv_empty_hint);
        
        rvFiles.setLayoutManager(new LinearLayoutManager(this));
        mFileAdapter = new FileBrowserAdapter(this);
        rvFiles.setAdapter(mFileAdapter);
        
        mCurrentDirectory = new File(storagePath);
        mDirectoryStack.clear();
        
        mFileAdapter.setOnItemClickListener(new FileBrowserAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(VideoFile item, int position) {
                mFileAdapter.setSelectedPosition(position);
                
                if (item.isDirectory()) {
                    mDirectoryStack.add(mCurrentDirectory);
                    mCurrentDirectory = new File(item.getPath());
                    loadFiles(tvCurrentPath, rvFiles, progressLoading, tvEmptyHint);
                } else {
                    mSelectedVideoPath = item.getPath();
                    showPlayModeDialog();
                }
            }

            @Override
            public boolean onItemLongClick(VideoFile item, int position) {
                return false;
            }
        });
        
        btnBack.setOnClickListener(v -> {
            if (!mDirectoryStack.isEmpty()) {
                mCurrentDirectory = mDirectoryStack.remove(mDirectoryStack.size() - 1);
                loadFiles(tvCurrentPath, rvFiles, progressLoading, tvEmptyHint);
            } else {
                mFileBrowserDialog.dismiss();
                showStorageSelectionDialog();
            }
        });
        
        btnBack.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
        });
        
        mFileBrowserDialog.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                if (!mDirectoryStack.isEmpty()) {
                    mCurrentDirectory = mDirectoryStack.remove(mDirectoryStack.size() - 1);
                    loadFiles(tvCurrentPath, rvFiles, progressLoading, tvEmptyHint);
                    return true;
                } else {
                    dialog.dismiss();
                    showStorageSelectionDialog();
                    return true;
                }
            }
            return false;
        });
        
        mFileBrowserDialog.show();
        
        btnBack.requestFocus();
        
        loadFiles(tvCurrentPath, rvFiles, progressLoading, tvEmptyHint);
    }

    private void loadFiles(TextView tvCurrentPath, RecyclerView rvFiles, ProgressBar progressBar, TextView tvEmptyHint) {
        tvCurrentPath.setText(mCurrentDirectory.getAbsolutePath());
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyHint.setVisibility(View.GONE);
        mFileAdapter.clearItems();
        
        mExecutor.execute(() -> {
            List<VideoFile> items = new ArrayList<>();
            
            File[] files = mCurrentDirectory.listFiles();
            if (files != null) {
                List<File> fileList = new ArrayList<>(Arrays.asList(files));
                Collections.sort(fileList, (f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                });
                
                for (File file : fileList) {
                    if (file.isDirectory() || StorageUtils.isVideoFile(file.getName())) {
                        items.add(new VideoFile(file));
                    }
                }
            }
            
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                
                if (items.isEmpty()) {
                    tvEmptyHint.setVisibility(View.VISIBLE);
                } else {
                    mFileAdapter.setItems(items);
                }
            });
        });
    }

    private void showPlayModeDialog() {
        String[] options = {getString(R.string.loop_single), getString(R.string.loop_folder)};
        int[] playModes = {VideoPlayerActivity.PLAY_MODE_LOOP_SINGLE, VideoPlayerActivity.PLAY_MODE_LOOP_FOLDER};
        
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.play_mode)
                .setSingleChoiceItems(options, 0, (dialog, which) -> {
                    mPlayMode = playModes[which];
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    mFileBrowserDialog.dismiss();
                    playSelectedVideo();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void playSelectedVideo() {
        if (mSelectedVideoPath == null || mSelectedVideoPath.isEmpty()) {
            Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        
        File videoFile = new File(mSelectedVideoPath);
        if (!videoFile.exists()) {
            Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        
        ArrayList<String> videoList = null;
        if (mPlayMode == VideoPlayerActivity.PLAY_MODE_LOOP_FOLDER && mCurrentDirectory != null) {
            videoList = new ArrayList<>();
            File[] files = mCurrentDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory() && StorageUtils.isVideoFile(file.getName())) {
                        videoList.add(file.getAbsolutePath());
                    }
                }
            }
        }
        
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_PATH, mSelectedVideoPath);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_NAME, videoFile.getName());
        intent.putExtra(VideoPlayerActivity.EXTRA_PLAY_MODE, mPlayMode);
        intent.putStringArrayListExtra(VideoPlayerActivity.EXTRA_VIDEO_LIST, videoList);
        intent.putExtra(VideoPlayerActivity.EXTRA_IS_NETWORK, false);
        startActivity(intent);
    }

    private void playNetworkVideo(String url) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_PATH, url);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_NAME, url);
        intent.putExtra(VideoPlayerActivity.EXTRA_PLAY_MODE, VideoPlayerActivity.PLAY_MODE_LOOP_SINGLE);
        intent.putExtra(VideoPlayerActivity.EXTRA_IS_NETWORK, true);
        startActivity(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogUtils.i("[MainActivity] onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.i("[MainActivity] onResume");
        mIsResumed = true;
        
        if (mMemoryHandler != null && mMemoryRunnable != null) {
            mMemoryHandler.post(mMemoryRunnable);
        }
        
        mWebView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogUtils.i("[MainActivity] onPause");
        mIsResumed = false;
        
        if (mMemoryHandler != null && mMemoryRunnable != null) {
            mMemoryHandler.removeCallbacks(mMemoryRunnable);
        }
        
        mWebView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogUtils.i("[MainActivity] onStop");
    }

    private void releaseDialogListeners() {
        if (mStorageDialog != null) {
            if (mStorageDialog.isShowing()) {
                mStorageDialog.dismiss();
            }
            mStorageDialog.setOnKeyListener(null);
            mStorageDialog = null;
        }
        
        if (mFileBrowserDialog != null) {
            if (mFileBrowserDialog.isShowing()) {
                mFileBrowserDialog.dismiss();
            }
            mFileBrowserDialog.setOnKeyListener(null);
            mFileBrowserDialog = null;
        }
        
        if (mStorageAdapter != null) {
            mStorageAdapter.setOnDeviceClickListener(null);
            mStorageAdapter = null;
        }
        
        if (mFileAdapter != null) {
            mFileAdapter.setOnItemClickListener(null);
            mFileAdapter = null;
        }
        
        mWebViewClient = null;
    }

    @Override
    protected void onDestroy() {
        LogUtils.i("[MainActivity] onDestroy");
        
        if (mMemoryHandler != null) {
            mMemoryHandler.removeCallbacksAndMessages(null);
            mMemoryHandler = null;
        }
        mMemoryRunnable = null;
        
        if (mWebView != null) {
            WebViewPerformanceManager.injectCleanupScript(mWebView);
            WebViewPerformanceManager.safeDestroy(mWebView);
            mWebView = null;
        }
        
        if (mExecutor != null && !mExecutor.isShutdown()) {
            mExecutor.shutdownNow();
            mExecutor = null;
        }
        
        releaseDialogListeners();
        
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupFullscreenMode();
        }
    }
}
