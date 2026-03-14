package com.haier.emptyscreen;

import android.content.Intent;
import android.media.AudioManager;
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

import androidx.appcompat.app.AppCompatActivity;

import com.haier.emptyscreen.utils.FocusUtils;
import com.haier.emptyscreen.utils.LogUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_PATH = "video_path";
    public static final String EXTRA_VIDEO_NAME = "video_name";
    public static final String EXTRA_PLAY_MODE = "play_mode";
    public static final String EXTRA_VIDEO_LIST = "video_list";
    public static final String EXTRA_IS_NETWORK = "is_network";

    public static final int PLAY_MODE_LOOP_SINGLE = 0;
    public static final int PLAY_MODE_LOOP_FOLDER = 1;

    private static final int CONTROL_HIDE_DELAY = 5000;
    private static final int PROGRESS_UPDATE_INTERVAL = 1000;

    private VideoView mVideoView;
    private LinearLayout mControlPanel;
    private TextView mTvCurrentTime;
    private TextView mTvTotalTime;
    private SeekBar mSeekbarProgress;
    private SeekBar mSeekbarVolume;
    private ImageButton mBtnPlayPause;
    private ImageButton mBtnStop;
    private ImageButton mBtnVolumeDown;
    private ImageButton mBtnVolumeUp;
    private ImageButton mBtnLoopMode;
    private TextView mTvVideoName;
    private ProgressBar mProgressBuffering;
    private LinearLayout mLayoutError;
    private TextView mTvErrorMessage;
    private ImageButton mBtnRetry;

    private Handler mHandler;
    private Runnable mProgressRunnable;
    private Runnable mHideControlRunnable;

    private String mVideoPath;
    private String mVideoName;
    private int mPlayMode = PLAY_MODE_LOOP_SINGLE;
    private ArrayList<String> mVideoList;
    private int mCurrentVideoIndex = 0;
    private boolean mIsNetwork = false;

    private AudioManager mAudioManager;
    private int mMaxVolume;
    private int mCurrentVolume;

    private boolean mIsPlaying = false;
    private boolean mIsControlVisible = true;
    private boolean mIsSeeking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtils.i("[VideoPlayerActivity] onCreate");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_video_player);

        parseIntent();
        initViews();
        initAudio();
        setupVideoView();
        setupControls();
        setupTVFocus();
        setupHandler();

        if (mVideoPath != null && !mVideoPath.isEmpty()) {
            playVideo(mVideoPath);
        } else {
            showError("无效的视频路径");
        }
    }

    private void parseIntent() {
        Intent intent = getIntent();
        mVideoPath = intent.getStringExtra(EXTRA_VIDEO_PATH);
        mVideoName = intent.getStringExtra(EXTRA_VIDEO_NAME);
        mPlayMode = intent.getIntExtra(EXTRA_PLAY_MODE, PLAY_MODE_LOOP_SINGLE);
        mVideoList = intent.getStringArrayListExtra(EXTRA_VIDEO_LIST);
        mIsNetwork = intent.getBooleanExtra(EXTRA_IS_NETWORK, false);

        if (mVideoList != null && mVideoPath != null) {
            mCurrentVideoIndex = mVideoList.indexOf(mVideoPath);
        }

        LogUtils.i("[VideoPlayerActivity] Video: " + mVideoName + ", Mode: " + mPlayMode);
    }

    private void initViews() {
        mVideoView = findViewById(R.id.video_view);
        mControlPanel = findViewById(R.id.control_panel);
        mTvCurrentTime = findViewById(R.id.tv_current_time);
        mTvTotalTime = findViewById(R.id.tv_total_time);
        mSeekbarProgress = findViewById(R.id.seekbar_progress);
        mSeekbarVolume = findViewById(R.id.seekbar_volume);
        mBtnPlayPause = findViewById(R.id.btn_play_pause);
        mBtnStop = findViewById(R.id.btn_stop);
        mBtnVolumeDown = findViewById(R.id.btn_volume_down);
        mBtnVolumeUp = findViewById(R.id.btn_volume_up);
        mBtnLoopMode = findViewById(R.id.btn_loop_mode);
        mTvVideoName = findViewById(R.id.tv_video_name);
        mProgressBuffering = findViewById(R.id.progress_buffering);
        mLayoutError = findViewById(R.id.layout_error);
        mTvErrorMessage = findViewById(R.id.tv_error_message);
        mBtnRetry = findViewById(R.id.btn_retry);

        if (mVideoName != null) {
            mTvVideoName.setText(mVideoName);
        }

        updateLoopModeButton();
    }

    private void initAudio() {
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (mAudioManager != null) {
            mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            mCurrentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mSeekbarVolume.setMax(mMaxVolume);
            mSeekbarVolume.setProgress(mCurrentVolume);
        }
    }

    private void setupVideoView() {
        mVideoView.setOnPreparedListener(mp -> {
            LogUtils.i("[VideoPlayerActivity] Video prepared");
            mProgressBuffering.setVisibility(View.GONE);
            mLayoutError.setVisibility(View.GONE);
            
            int duration = mVideoView.getDuration();
            mSeekbarProgress.setMax(duration);
            mTvTotalTime.setText(formatTime(duration));
            
            startProgressUpdate();
            mVideoView.start();
            mIsPlaying = true;
            updatePlayPauseButton();
        });

        mVideoView.setOnCompletionListener(mp -> {
            LogUtils.i("[VideoPlayerActivity] Video completed");
            onVideoCompleted();
        });

        mVideoView.setOnErrorListener((mp, what, extra) -> {
            LogUtils.e("[VideoPlayerActivity] Video error: what=" + what + ", extra=" + extra);
            showError("视频播放失败");
            return true;
        });

        mVideoView.setOnInfoListener((mp, what, extra) -> {
            switch (what) {
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    mProgressBuffering.setVisibility(View.VISIBLE);
                    LogUtils.d("[VideoPlayerActivity] Buffering start");
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    mProgressBuffering.setVisibility(View.GONE);
                    LogUtils.d("[VideoPlayerActivity] Buffering end");
                    break;
            }
            return false;
        });
    }

    private void setupControls() {
        mBtnPlayPause.setOnClickListener(v -> togglePlayPause());

        mBtnStop.setOnClickListener(v -> {
            mVideoView.stopPlayback();
            mIsPlaying = false;
            updatePlayPauseButton();
            finish();
        });

        mBtnVolumeDown.setOnClickListener(v -> adjustVolume(-1));
        mBtnVolumeUp.setOnClickListener(v -> adjustVolume(1));

        mSeekbarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                    mCurrentVolume = progress;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        mSeekbarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mTvCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mIsSeeking = false;
                mVideoView.seekTo(seekBar.getProgress());
            }
        });

        mBtnLoopMode.setOnClickListener(v -> toggleLoopMode());

        mBtnRetry.setOnClickListener(v -> {
            hideError();
            playVideo(mVideoPath);
        });

        mVideoView.setOnClickListener(v -> toggleControlPanel());
        mControlPanel.setOnClickListener(v -> resetHideControlTimer());
    }

    private void setupTVFocus() {
        mBtnPlayPause.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
            if (hasFocus) resetHideControlTimer();
        });
        
        mBtnStop.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
            if (hasFocus) resetHideControlTimer();
        });
        
        mBtnVolumeDown.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
            if (hasFocus) resetHideControlTimer();
        });
        
        mBtnVolumeUp.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
            if (hasFocus) resetHideControlTimer();
        });
        
        mBtnLoopMode.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
            if (hasFocus) resetHideControlTimer();
        });
        
        mBtnRetry.setOnFocusChangeListener((v, hasFocus) -> {
            FocusUtils.applyFocusAnimation(v, hasFocus);
        });
        
        mSeekbarProgress.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setScaleX(1.1f);
                v.setScaleY(1.1f);
                resetHideControlTimer();
            } else {
                v.setScaleX(1.0f);
                v.setScaleY(1.0f);
            }
        });
        
        mSeekbarVolume.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setScaleX(1.1f);
                v.setScaleY(1.1f);
                resetHideControlTimer();
            } else {
                v.setScaleX(1.0f);
                v.setScaleY(1.0f);
            }
        });
    }

    private void setupHandler() {
        mHandler = new Handler(Looper.getMainLooper());

        mProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mIsPlaying && !mIsSeeking && mVideoView.isPlaying()) {
                    int position = mVideoView.getCurrentPosition();
                    mSeekbarProgress.setProgress(position);
                    mTvCurrentTime.setText(formatTime(position));
                }
                mHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL);
            }
        };

        mHideControlRunnable = new Runnable() {
            @Override
            public void run() {
                if (mIsPlaying) {
                    hideControlPanel();
                }
            }
        };
    }

    private void playVideo(String path) {
        LogUtils.i("[VideoPlayerActivity] Playing: " + path);
        
        mProgressBuffering.setVisibility(View.VISIBLE);
        mLayoutError.setVisibility(View.GONE);

        try {
            Uri uri;
            if (mIsNetwork) {
                uri = Uri.parse(path);
            } else {
                uri = Uri.fromFile(new File(path));
            }
            mVideoView.setVideoURI(uri);
        } catch (Exception e) {
            LogUtils.e("[VideoPlayerActivity] Failed to play video: " + e.getMessage());
            showError("无法播放视频: " + e.getMessage());
        }
    }

    private void onVideoCompleted() {
        if (mPlayMode == PLAY_MODE_LOOP_SINGLE) {
            mVideoView.seekTo(0);
            mVideoView.start();
        } else if (mPlayMode == PLAY_MODE_LOOP_FOLDER && mVideoList != null && !mVideoList.isEmpty()) {
            mCurrentVideoIndex = (mCurrentVideoIndex + 1) % mVideoList.size();
            String nextVideo = mVideoList.get(mCurrentVideoIndex);
            mVideoPath = nextVideo;
            mVideoName = new File(nextVideo).getName();
            mTvVideoName.setText(mVideoName);
            playVideo(nextVideo);
        } else {
            mIsPlaying = false;
            updatePlayPauseButton();
        }
    }

    private void togglePlayPause() {
        if (mVideoView.isPlaying()) {
            mVideoView.pause();
            mIsPlaying = false;
        } else {
            mVideoView.start();
            mIsPlaying = true;
            startProgressUpdate();
        }
        updatePlayPauseButton();
        resetHideControlTimer();
    }

    private void updatePlayPauseButton() {
        if (mIsPlaying) {
            mBtnPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            mBtnPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    private void toggleLoopMode() {
        if (mPlayMode == PLAY_MODE_LOOP_SINGLE) {
            mPlayMode = PLAY_MODE_LOOP_FOLDER;
            Toast.makeText(this, R.string.loop_folder, Toast.LENGTH_SHORT).show();
        } else {
            mPlayMode = PLAY_MODE_LOOP_SINGLE;
            Toast.makeText(this, R.string.loop_single, Toast.LENGTH_SHORT).show();
        }
        updateLoopModeButton();
    }

    private void updateLoopModeButton() {
        if (mPlayMode == PLAY_MODE_LOOP_SINGLE) {
            mBtnLoopMode.setImageResource(R.drawable.ic_loop_one);
        } else {
            mBtnLoopMode.setImageResource(R.drawable.ic_loop_all);
        }
    }

    private void adjustVolume(int direction) {
        if (mAudioManager == null) return;

        int newVolume = mCurrentVolume + direction;
        newVolume = Math.max(0, Math.min(mMaxVolume, newVolume));

        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
        mCurrentVolume = newVolume;
        mSeekbarVolume.setProgress(mCurrentVolume);

        Toast.makeText(this, "音量: " + (mCurrentVolume * 100 / mMaxVolume) + "%", Toast.LENGTH_SHORT).show();
    }

    private void toggleControlPanel() {
        if (mIsControlVisible) {
            hideControlPanel();
        } else {
            showControlPanel();
        }
    }

    private void showControlPanel() {
        mIsControlVisible = true;
        mControlPanel.setVisibility(View.VISIBLE);
        mBtnPlayPause.requestFocus();
        resetHideControlTimer();
    }

    private void hideControlPanel() {
        mIsControlVisible = false;
        mControlPanel.setVisibility(View.GONE);
    }

    private void resetHideControlTimer() {
        mHandler.removeCallbacks(mHideControlRunnable);
        mHandler.postDelayed(mHideControlRunnable, CONTROL_HIDE_DELAY);
    }

    private void startProgressUpdate() {
        mHandler.post(mProgressRunnable);
    }

    private void stopProgressUpdate() {
        mHandler.removeCallbacks(mProgressRunnable);
    }

    private void showError(String message) {
        mProgressBuffering.setVisibility(View.GONE);
        mLayoutError.setVisibility(View.VISIBLE);
        mTvErrorMessage.setText(message);
    }

    private void hideError() {
        mLayoutError.setVisibility(View.GONE);
    }

    private String formatTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        int hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogUtils.i("[VideoPlayerActivity] onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.i("[VideoPlayerActivity] onResume");
        if (!mVideoView.isPlaying() && mIsPlaying) {
            mVideoView.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogUtils.i("[VideoPlayerActivity] onPause");
        if (mVideoView.isPlaying()) {
            mVideoView.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogUtils.i("[VideoPlayerActivity] onStop");
    }

    @Override
    protected void onDestroy() {
        LogUtils.i("[VideoPlayerActivity] onDestroy");
        
        stopProgressUpdate();
        mHandler.removeCallbacks(mHideControlRunnable);
        
        if (mVideoView != null) {
            mVideoView.stopPlayback();
        }
        
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case android.view.KeyEvent.KEYCODE_SPACE:
                togglePlayPause();
                return true;
            case android.view.KeyEvent.KEYCODE_MEDIA_STOP:
                mVideoView.stopPlayback();
                finish();
                return true;
            case android.view.KeyEvent.KEYCODE_VOLUME_UP:
                adjustVolume(1);
                return true;
            case android.view.KeyEvent.KEYCODE_VOLUME_DOWN:
                adjustVolume(-1);
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                seekBackward();
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                seekForward();
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
            case android.view.KeyEvent.KEYCODE_ENTER:
                toggleControlPanel();
                return true;
            case android.view.KeyEvent.KEYCODE_MENU:
                toggleLoopMode();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void seekForward() {
        if (mVideoView.isPlaying() || mVideoView.canSeekForward()) {
            int position = mVideoView.getCurrentPosition() + 10000;
            position = Math.min(position, mVideoView.getDuration());
            mVideoView.seekTo(position);
            mSeekbarProgress.setProgress(position);
            mTvCurrentTime.setText(formatTime(position));
            Toast.makeText(this, "快进 10秒", Toast.LENGTH_SHORT).show();
        }
    }

    private void seekBackward() {
        if (mVideoView.isPlaying() || mVideoView.canSeekBackward()) {
            int position = mVideoView.getCurrentPosition() - 10000;
            position = Math.max(position, 0);
            mVideoView.seekTo(position);
            mSeekbarProgress.setProgress(position);
            mTvCurrentTime.setText(formatTime(position));
            Toast.makeText(this, "快退 10秒", Toast.LENGTH_SHORT).show();
        }
    }
}
