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
import com.haier.emptyscreen.utils.MemoryCleaner;
import com.haier.emptyscreen.utils.MemoryUtils;
import com.haier.emptyscreen.utils.PrefsManager;
import com.haier.logger.HLogger;

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

        // 拉起链路终点：能走到这里说明 Activity 真的被系统创建了（区别于 startActivity 被静默拦截）。
        // 打印来源信息便于判断本次是被谁拉起的（开机重试 / 桌面 / 通知 / STR唤醒）。
        Intent launchIntent = getIntent();
        HLogger.i(TAG + " onCreate - launched. action="
                + (launchIntent == null ? "null" : launchIntent.getAction())
                + ", flags=0x" + (launchIntent == null ? "0"
                        : Integer.toHexString(launchIntent.getFlags()))
                + ", savedState=" + (savedInstanceState != null));

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
        // 自愈常驻前台服务：服务被 ROM 杀掉后 SCREEN_ON 接收器消失、STR 唤醒失效。
        // LauncherActivity 回到前台时确保服务存活，让 STR 唤醒链路自愈。
        ForegroundService.ensureRunning(this);
        // 拉起成功的真正终点：Activity 进入前台可见。开机/STR场景看到这条即代表本次拉起成功。
        HLogger.i(TAG + " onResume [source=" + mPrefsManager.getLaunchSource()
                + "] activity now in foreground");
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

        String source = mPrefsManager.getLaunchSource();
        long bootElapsed = mPrefsManager.getLastBootElapsed();
        if (bootElapsed > 0) {
            long elapsedMillis = SystemClock.elapsedRealtime() - bootElapsed;
            int elapsedSeconds = (int) (elapsedMillis / 1000L);
            HLogger.i(TAG + " launch SUCCEEDED [source=" + source + "] elapsed="
                    + elapsedSeconds + "s since trigger");
            mPrefsManager.updateAdaptiveDelay(elapsedSeconds);
        } else {
            HLogger.i(TAG + " launch SUCCEEDED [source=" + source + "] (no trigger baseline)");
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
        HLogger.d(TAG + " ForegroundService started");
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
        HLogger.w(TAG + " Memory usage >= " + threshold + "%, cleaning up...");

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

        HLogger.i(TAG + " Will launch MainActivity after " + delaySeconds + " seconds");

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
            HLogger.i(TAG + " Launched MainActivity successfully");
        } catch (Exception e) {
            HLogger.e(TAG + " Failed to launch MainActivity: " + e.getMessage());
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
