package com.haier.emptyscreen;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.MemoryCleaner;
import com.haier.emptyscreen.utils.MemoryUtils;

public class LauncherActivity extends AppCompatActivity {

    private static final String TAG = "LauncherActivity";
    private static final long LAUNCH_DELAY_MS = 3000;
    private static final float MEMORY_THRESHOLD = 80.0f;
    private static final long MEMORY_CHECK_INTERVAL_MS = 500;

    private ProgressBar mProgressBar;
    private TextView mTvMemoryWarning;
    
    private CountDownTimer mLaunchTimer;
    private Handler mMemoryCheckHandler;
    private Runnable mMemoryCheckRunnable;
    private boolean mIsLaunching = false;
    private boolean mMemoryWarningShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupFullscreenMode();
        setContentView(R.layout.activity_launcher);
        
        initViews();
        setupMemoryMonitor();
        startLaunchTimer();
        
        LogUtils.i("[" + TAG + "] Created");
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
        mProgressBar = findViewById(R.id.progress_bar);
        mTvMemoryWarning = findViewById(R.id.tv_memory_warning);
    }

    private void setupMemoryMonitor() {
        mMemoryCheckHandler = new Handler(Looper.getMainLooper());
        mMemoryCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkMemoryAndHandle();
                if (!mIsLaunching) {
                    mMemoryCheckHandler.postDelayed(this, MEMORY_CHECK_INTERVAL_MS);
                }
            }
        };
        mMemoryCheckHandler.post(mMemoryCheckRunnable);
    }

    private void checkMemoryAndHandle() {
        float memoryUsage = MemoryUtils.getSystemMemoryUsagePercent(this);
        
        if (memoryUsage >= MEMORY_THRESHOLD) {
            handleHighMemoryUsage(memoryUsage);
        } else if (mMemoryWarningShown) {
            mTvMemoryWarning.setVisibility(View.GONE);
            mMemoryWarningShown = false;
        }
    }

    private void handleHighMemoryUsage(float memoryUsage) {
        LogUtils.w("[" + TAG + "] High memory: " + String.format("%.1f%%", memoryUsage));
        
        if (!mMemoryWarningShown) {
            mTvMemoryWarning.setVisibility(View.VISIBLE);
            mMemoryWarningShown = true;
        }
        
        if (!mIsLaunching) {
            mIsLaunching = true;
            if (mLaunchTimer != null) {
                mLaunchTimer.cancel();
            }
            performMemoryCleanupAndRestart();
        }
    }

    private void performMemoryCleanupAndRestart() {
        LogUtils.i("[" + TAG + "] Cleanup and restart");
        
        new Thread(() -> {
            MemoryCleaner.cleanMemory(this);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            runOnUiThread(this::restartApplication);
        }).start();
    }

    private void restartApplication() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            activityManager.killBackgroundProcesses(getPackageName());
        }
        
        Intent intent = new Intent(this, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        
        finish();
        Runtime.getRuntime().exit(0);
    }

    private void startLaunchTimer() {
        mLaunchTimer = new CountDownTimer(LAUNCH_DELAY_MS, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                if (!mIsLaunching) {
                    launchMainActivity();
                }
            }
        };
        mLaunchTimer.start();
    }

    private void launchMainActivity() {
        mIsLaunching = true;
        
        float memoryUsage = MemoryUtils.getSystemMemoryUsagePercent(this);
        if (memoryUsage >= MEMORY_THRESHOLD) {
            LogUtils.w("[" + TAG + "] Memory still high: " + String.format("%.1f%%", memoryUsage));
            performMemoryCleanupAndRestart();
            return;
        }
        
        LogUtils.i("[" + TAG + "] Launching MainActivity");
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMemoryCheckHandler != null && mMemoryCheckRunnable != null) {
            mMemoryCheckHandler.post(mMemoryCheckRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMemoryCheckHandler != null && mMemoryCheckRunnable != null) {
            mMemoryCheckHandler.removeCallbacks(mMemoryCheckRunnable);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogUtils.i("[" + TAG + "] onStop");
    }

    @Override
    protected void onDestroy() {
        LogUtils.i("[" + TAG + "] onDestroy");
        
        if (mLaunchTimer != null) {
            mLaunchTimer.cancel();
            mLaunchTimer = null;
        }
        
        if (mMemoryCheckHandler != null) {
            mMemoryCheckHandler.removeCallbacksAndMessages(null);
            mMemoryCheckHandler = null;
        }
        mMemoryCheckRunnable = null;
        
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
