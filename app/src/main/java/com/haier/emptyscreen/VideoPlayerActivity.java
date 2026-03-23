package com.haier.emptyscreen;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.haier.emptyscreen.utils.LogUtils;
import com.haier.emptyscreen.utils.MemoryCleaner;

public class VideoPlayerActivity extends Activity {

    private static final String TAG = "[VideoPlayerActivity]";

    public static final String EXTRA_VIDEO_URI = "video_uri";
    public static final String EXTRA_IS_LOCAL = "is_local";

    private VideoView mVideoView;
    private LinearLayout mControlPanel;
    private ImageButton mPlayPauseButton;
    private ImageButton mStopButton;
    private SeekBar mSeekBar;
    private TextView mCurrentTimeText;
    private TextView mTotalTimeText;
    private TextView mVideoNameText;
    private ProgressBar mBufferingProgress;

    private Uri mVideoUri;
    private boolean mIsLocalVideo;
    private Handler mHandler;
    private Runnable mSeekBarRunnable;

    private boolean mIsPlaying = false;
    private boolean mIsControlsVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        parseIntentData();

        if (mVideoUri == null) {
            LogUtils.e(TAG + " Video URI is null, finishing activity");
            finish();
            return;
        }

        initializeViews();
        setupVideoPlayer();
    }

    private void parseIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            String uriString = intent.getStringExtra(EXTRA_VIDEO_URI);
            mIsLocalVideo = intent.getBooleanExtra(EXTRA_IS_LOCAL, true);

            if (uriString != null) {
                mVideoUri = Uri.parse(uriString);
            }
        }

        LogUtils.d(TAG + " Video URI: " + mVideoUri + ", Is local: " + mIsLocalVideo);
    }

    private void initializeViews() {
        mVideoView = findViewById(R.id.video_view);
        mControlPanel = findViewById(R.id.control_panel);
        mPlayPauseButton = findViewById(R.id.btn_play_pause);
        mStopButton = findViewById(R.id.btn_stop);
        mSeekBar = findViewById(R.id.seekbar_progress);
        mCurrentTimeText = findViewById(R.id.tv_current_time);
        mTotalTimeText = findViewById(R.id.tv_total_time);
        mVideoNameText = findViewById(R.id.tv_video_name);
        mBufferingProgress = findViewById(R.id.progress_buffering);

        if (mVideoUri != null) {
            mVideoNameText.setText(mVideoUri.getLastPathSegment());
        }

        setupClickListeners();
        setupSeekBarListener();
    }

    private void setupClickListeners() {
        mPlayPauseButton.setOnClickListener(v -> togglePlayPause());
        mStopButton.setOnClickListener(v -> stopVideo());

        mVideoView.setOnClickListener(v -> {
            if (mIsControlsVisible) {
                hideControls();
            } else {
                showControls();
            }
        });
    }

    private void setupSeekBarListener() {
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateCurrentTimeText(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                removeSeekBarUpdates();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int position = seekBar.getProgress();
                mVideoView.seekTo(position);
                startSeekBarUpdates();
            }
        });
    }

    private void setupVideoPlayer() {
        mVideoView.setVideoURI(mVideoUri);

        mVideoView.setOnPreparedListener(mp -> {
            LogUtils.i(TAG + " Video prepared, duration: " + mp.getDuration() + "ms");
            mSeekBar.setMax(mp.getDuration());
            mTotalTimeText.setText(formatTime(mp.getDuration()));
            mBufferingProgress.setVisibility(View.GONE);
            startVideoPlayback();
        });

        mVideoView.setOnCompletionListener(mp -> {
            LogUtils.i(TAG + " Video completed");
            mPlayPauseButton.setImageResource(R.drawable.ic_play);
            mIsPlaying = false;
            removeSeekBarUpdates();
        });

        mVideoView.setOnErrorListener((mp, what, extra) -> {
            LogUtils.e(TAG + " Video error: what=" + what + ", extra=" + extra);
            Toast.makeText(this, "播放失败：" + getErrorMessage(what), Toast.LENGTH_SHORT).show();
            mBufferingProgress.setVisibility(View.GONE);
            return true;
        });

        mVideoView.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                mBufferingProgress.setVisibility(View.VISIBLE);
            } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                mBufferingProgress.setVisibility(View.GONE);
            }
            return true;
        });
    }

    private void startVideoPlayback() {
        if (!mIsPlaying) {
            mVideoView.start();
            mIsPlaying = true;
            mPlayPauseButton.setImageResource(R.drawable.ic_pause);
            startSeekBarUpdates();
            LogUtils.d(TAG + " Video playback started");
        }
    }

    private void togglePlayPause() {
        if (mIsPlaying) {
            mVideoView.pause();
            mIsPlaying = false;
            mPlayPauseButton.setImageResource(R.drawable.ic_play);
            LogUtils.d(TAG + " Video paused");
        } else {
            mVideoView.start();
            mIsPlaying = true;
            mPlayPauseButton.setImageResource(R.drawable.ic_pause);
            startSeekBarUpdates();
            LogUtils.d(TAG + " Video resumed");
        }
    }

    private void stopVideo() {
        mVideoView.stopPlayback();
        mIsPlaying = false;
        mPlayPauseButton.setImageResource(R.drawable.ic_play);
        mSeekBar.setProgress(0);
        mCurrentTimeText.setText("00:00");
        removeSeekBarUpdates();
        LogUtils.d(TAG + " Video stopped");
    }

    private void showControls() {
        mControlPanel.setVisibility(View.VISIBLE);
        mIsControlsVisible = true;
    }

    private void hideControls() {
        mControlPanel.setVisibility(View.GONE);
        mIsControlsVisible = false;
    }

    private void startSeekBarUpdates() {
        mHandler = new Handler(Looper.getMainLooper());
        mSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (mIsPlaying && mVideoView != null) {
                    int position = mVideoView.getCurrentPosition();
                    mSeekBar.setProgress(position);
                    updateCurrentTimeText(position);
                    mHandler.postDelayed(this, 1000);
                }
            }
        };
        mHandler.post(mSeekBarRunnable);
    }

    private void removeSeekBarUpdates() {
        if (mHandler != null && mSeekBarRunnable != null) {
            mHandler.removeCallbacks(mSeekBarRunnable);
        }
    }

    private void updateCurrentTimeText(int positionMs) {
        mCurrentTimeText.setText(formatTime(positionMs));
    }

    private String formatTime(int timeMs) {
        int totalSeconds = timeMs / 1000;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                return "未知错误";
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                return "服务器错误";
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                return "不支持的格式";
            default:
                return "错误代码：" + errorCode;
        }
    }

    @Override
    protected void onDestroy() {
        if (mVideoView != null) {
            mVideoView.suspend();
        }
        removeSeekBarUpdates();
        MemoryCleaner.cleanMemory(this);
        super.onDestroy();
    }
}
