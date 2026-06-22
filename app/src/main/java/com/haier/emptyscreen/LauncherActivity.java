package com.haier.emptyscreen;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.haier.emptyscreen.service.ForegroundService;
import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.MemoryCleaner;
import com.haier.emptyscreen.utils.MemoryUtils;
import com.haier.emptyscreen.utils.PrefsManager;

public class LauncherActivity extends Activity {

    private static final String TAG = "[LauncherActivity]";

    private ProgressBar mProgressBar;
    private TextView mMemoryWarningText;

    private PrefsManager mPrefsManager;
    private Handler mHandler;

    private boolean mIsDestroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }

        setContentView(R.layout.activity_launcher);

        mPrefsManager = PrefsManager.getInstance(this);
        mHandler = new Handler(Looper.getMainLooper());

        initializeViews();

        startForegroundService();

        checkMemoryAndContinue();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 成功进入前台：标记本次开机拉起成功，使后续重试自动空转，并学习本机真实开机耗时
        markBootLaunchSucceeded();
    }

    /**
     * 标记开机拉起成功并更新自适应延迟
     *
     * <p>用"开机广播触发时刻"到"现在"的耗时，作为本机真实的开机进首页时间，
     * 通过指数移动平均收敛 {@link PrefsManager} 中的自适应延迟，下次开机更精准。</p>
     */
    private void markBootLaunchSucceeded() {
        if (mPrefsManager.isBootLaunchSuccess()) {
            return;
        }
        mPrefsManager.setBootLaunchSuccess(true);

        long bootElapsed = mPrefsManager.getLastBootElapsed();
        if (bootElapsed > 0) {
            long elapsedMillis = SystemClock.elapsedRealtime() - bootElapsed;
            int elapsedSeconds = (int) (elapsedMillis / 1000L);
            LogUtils.i(TAG + " Boot launch succeeded, elapsed=" + elapsedSeconds + "s since boot");
            mPrefsManager.updateAdaptiveDelay(elapsedSeconds);
        }
    }

    private void initializeViews() {
        mProgressBar = findViewById(R.id.progress_bar);
        mMemoryWarningText = findViewById(R.id.tv_memory_warning);
    }

    private void startForegroundService() {
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        LogUtils.d(TAG + " ForegroundService started");
    }

    private void checkMemoryAndContinue() {
        int threshold = mPrefsManager.getMemoryCleanThreshold();
        float currentUsage = MemoryUtils.getSystemMemoryUsagePercent(this);

        if (currentUsage >= threshold) {
            performMemoryCleanup(threshold);
        } else {
            proceedToMain();
        }
    }

    private void performMemoryCleanup(int threshold) {
        LogUtils.w(TAG + " Memory usage >= " + threshold + "%, cleaning up...");

        showMemoryWarning("正在优化内存...");

        MemoryCleaner.CleanResult result = MemoryCleaner.cleanMemory(this);

        float afterPercent = result.afterPercent;
        if (afterPercent >= threshold) {
            showMemoryWarning("内存占用过高，正在重启...");
            restartAppDelayed();
        } else {
            proceedToMain();
        }
    }

    private void showMemoryWarning(String message) {
        if (!mIsDestroyed && mMemoryWarningText != null) {
            mMemoryWarningText.setText(message);
            mMemoryWarningText.setVisibility(View.VISIBLE);
        }
    }

    private void restartAppDelayed() {
        mHandler.postDelayed(() -> {
            if (!mIsDestroyed) {
                EmptyScreenApplication.getInstance().restartApp();
            }
        }, 2000);
    }

    private void proceedToMain() {
        int delaySeconds = mPrefsManager.getBootDelaySeconds();
        long delayMillis = delaySeconds * 1000L;

        LogUtils.i(TAG + " Will launch MainActivity after " + delaySeconds + " seconds");

        mHandler.postDelayed(() -> {
            if (!mIsDestroyed) {
                launchMainActivity();
            }
        }, delayMillis);
    }

    private void launchMainActivity() {
        try {
            Intent intent = new Intent(LauncherActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            LogUtils.i(TAG + " Launched MainActivity successfully");
        } catch (Exception e) {
            LogUtils.e(TAG + " Failed to launch MainActivity: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        mIsDestroyed = true;
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }
}
