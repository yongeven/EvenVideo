package com.even.video;

import java.io.File;
import java.util.Locale;
import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.provider.Settings.System;


public class VideoMainActivity extends Activity implements 
SurfaceHolder.Callback
,OnPreparedListener
,OnErrorListener
,OnCompletionListener
,OnSeekCompleteListener
,OnVideoSizeChangedListener
,OnSeekBarChangeListener
{
	private static final int STATE_ERROR              = -1; //错误
	private static final int STATE_IDLE               = 0; //空闲
	private static final int STATE_PREPARING          = 1; //准备
	private static final int STATE_PREPARED           = 2; //准备完毕
	private static final int STATE_PLAYING            = 3; //播放中
	private static final int STATE_PAUSED             = 4; //暂停
	private static final int STATE_PLAYBACK_COMPLETED = 5; //播放完成
	/*
	 * 视频宽高
	 */
	private int         mVideoWidth; 
	private int         mVideoHeight;
	private int         mSurfaceWidth;
	private int         mSurfaceHeight;

	private int mMediaPlayerState = STATE_IDLE;
	private static final String TAG = AnimationUtil.class.getSimpleName();
	private MediaPlayer mMediaPlayer = null;
	private static final int SHOW_PROGRESS = 0x01;
	private static final int GONE_PLAYUI = 0x02;
	private static final int VISIBLE_PLAYUI = 0x03;
	/*
	 * the path of the file, or the http/rtsp URL of the stream you want to play
	 */
	//	String path = "/sdcard/FOOD_2560_1440.mp4";
	String path = "/sdcard/变形金刚_1280_720.mp4";

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			try {
				switch (msg.what) {
				case SHOW_PROGRESS:{
					if (isPlaying()) {
						int duration = mMediaPlayer.getDuration();  //视频长度
						int position = mMediaPlayer.getCurrentPosition(); //当前播放位置
						if(duration < 0) {
							duration = 0;
						}
						if(position < 0) {
							position = 0;
						}
						/*
						 * 换算总长度 00:00格式显示
						 */
						int totaltime = duration / 1000;
						int stotaltime = totaltime;
						int mtotaltime = stotaltime / 60;
						int htotaltime = mtotaltime / 60;
						stotaltime %= 60;
						mtotaltime %= 60;
						htotaltime %= 24;
						if(htotaltime == 0) {
							mTotaltime.setText(String.format(Locale.US, "%d:%02d", mtotaltime, stotaltime));
						} else {
							mTotaltime.setText(String.format(Locale.US, "%d:%02d:%02d", htotaltime, mtotaltime, stotaltime));
						}
						/*
						 * 换算当前进度 00:00格式显示
						 */
						int currenttime = position / 1000;
						int scurrenttime = currenttime;
						int mcurrenttime = scurrenttime / 60;
						int hcurrenttime = mcurrenttime / 60;
						scurrenttime %= 60;
						mcurrenttime %= 60;
						hcurrenttime %= 24;
						if(hcurrenttime == 0) {
							mCurrenttime.setText(String.format(Locale.US, "%d:%02d", mcurrenttime, scurrenttime));
						} else {
							mCurrenttime.setText(String.format(Locale.US, "%d:%02d:%02d", hcurrenttime, mcurrenttime, scurrenttime));
						}
						/*
						 * 设置进度条属性
						 */
						ProgressBar progress = (ProgressBar)findViewById(R.id.progress);
						progress.setMax(duration);
						progress.setProgress(position);
					}
					((ImageView)findViewById(R.id.pp)).getDrawable().setLevel(isPlaying() ? 1 : 0);
					mHandler.removeMessages(SHOW_PROGRESS);
					mHandler.sendEmptyMessageDelayed(SHOW_PROGRESS, 250);
				}
				break;
				case GONE_PLAYUI:
					mHandler.removeMessages(GONE_PLAYUI);
					mPlayui.setVisibility(View.GONE);
					mPlayui.setAnimation(AnimationUtil.moveLocationToBottom());
					mCt.setAnimation(AnimationUtil.moveLocationToTop());
					mCt.setVisibility(View.GONE);
					break;
				case VISIBLE_PLAYUI:
					mHandler.sendEmptyMessageDelayed(GONE_PLAYUI,6000);
					if (mCt.getVisibility() != View.VISIBLE) {
						mCt.setVisibility(View.VISIBLE);
						mCt.setAnimation(AnimationUtil.moveTopToLocation());
					}
					if(mPlayui.getVisibility() != View.VISIBLE){
						mPlayui.setVisibility(View.VISIBLE);
						mPlayui.setAnimation(AnimationUtil.moveBottomToLocation());
					}
					break;
				}
			} catch (Exception e) {

			}
		}
	};



	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initView();
		initData();
	}

	private SurfaceView mSurfaceView;
	private SeekBar mSeekBar;  //进度条
	private TextView mCurrenttime; //当前进度tx
	private TextView mTotaltime; //总长度tx
	private RelativeLayout mPlayui; //控制模块
	private ImageView mCt; //大窗口
	private void initView() {
		mVideoWidth = 0;
		mVideoHeight = 0;
		mSurfaceView = (SurfaceView) findViewById(R.id.video);
		mSurfaceView.getHolder().addCallback(this);
		mSeekBar = (SeekBar) findViewById(R.id.progress);
		mSeekBar.setOnSeekBarChangeListener(this);
		mCurrenttime = (TextView) findViewById(R.id.currenttime);
		mTotaltime = (TextView) findViewById(R.id.totaltime);
		mPlayui = (RelativeLayout) findViewById(R.id.playui);
		mCt = (ImageView) findViewById(R.id.ct);
	}

	private void initData() {
		File file = new File(path);
		if (file.exists() && file.length() > 0) {
			try {
				/*
				 * 初始化MediaPlayer
				 */
				mMediaPlayer = new MediaPlayer();
				mMediaPlayer.setOnErrorListener(this);
				mMediaPlayer.setOnCompletionListener(this);
				mMediaPlayer.setOnSeekCompleteListener(this);
				mMediaPlayer.setOnPreparedListener(this);
				mMediaPlayer.setOnPreparedListener(this);
				mMediaPlayer.setOnVideoSizeChangedListener(this);
				mMediaPlayer.setDataSource(path);
				mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mMediaPlayer.setScreenOnWhilePlaying(true); //保持屏幕开启
				mMediaPlayer.prepareAsync();
				mMediaPlayerState = STATE_PREPARING;
			} catch (Exception e) {
				Log.i(TAG, e.toString());
			}
		}
	}

	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.pp:
			if (isPlaying()) {
				mPause();
			}else{
				mStart();
			}
			break;
		}
		mHandler.removeMessages(GONE_PLAYUI); 
		mHandler.sendEmptyMessage(VISIBLE_PLAYUI);
	}

	/*
	 * 快进
	 */
	private void mFastforward(){
		if (isPlaying()) {
			int pos = mMediaPlayer.getCurrentPosition();
			pos += 15000;
			if (pos > 0 && pos < mMediaPlayer.getDuration()) {
				mSeekTo(pos);
			}
		}
	}

	/*
	 * 快退
	 */
	private void mRewind(){
		if (isPlaying()) {
			int pos = mMediaPlayer.getCurrentPosition();
			pos -= 15000;
			if (pos > 0 && pos < mMediaPlayer.getDuration()) {
				mSeekTo(pos);
			}
		}
	}

	/*
	 * 开始播放
	 */
	private void mStart(){
		mHandler.removeMessages(SHOW_PROGRESS);
		mHandler.sendEmptyMessageDelayed(SHOW_PROGRESS, 250);
		if (isInPlaybackState()) {
			mMediaPlayer.start();
			mMediaPlayerState = STATE_PLAYING;
		}
	}

	/*
	 * 暂停播放
	 */
	private void mPause(){
		if (isPlaying()) {
			mMediaPlayer.pause();
		}
		mMediaPlayerState = STATE_PAUSED;
	}

	/*
	 * 设置进度
	 */
	private void mSeekTo(int sk){
		if (isInPlaybackState()) {
			mMediaPlayer.seekTo(sk);
		}
	}

	/*
	 * 停止播放
	 */
	private void Stop(){
		mHandler.removeMessages(SHOW_PROGRESS);
		if (mMediaPlayer != null) {
			mMediaPlayer.stop();
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
	}

	/*
	 * 判断是否在播放状态
	 */
	private boolean isPlaying(){
		return mMediaPlayer.isPlaying()&&isInPlaybackState();
	}

	/*
	 * surface创建
	 */
	@Override
	public void surfaceCreated(SurfaceHolder surfaceHolder) {
		mMediaPlayer.setDisplay(surfaceHolder);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
	}

	/*
	 * 当装载流媒体完毕的时候回调
	 */
	@Override
	public void onPrepared(MediaPlayer mp) {
		mMediaPlayerState = STATE_PREPARED;
	}

	/*
	 * 当播放中发生错误的时候回调
	 */
	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		mMediaPlayerState = STATE_ERROR;
		return false;
	}

	/*
	 * 当流媒体播放完毕的时候回调
	 */
	@Override
	public void onCompletion(MediaPlayer mp) {
		mMediaPlayerState = STATE_PLAYBACK_COMPLETED;
	}

	/*
	 * 使用seekTo()设置播放进度的时候回调
	 */
	@Override
	public void onSeekComplete(MediaPlayer mp) {
	}

	@Override
	protected void onResume() {
		super.onResume();
		mHandler.sendEmptyMessageDelayed(GONE_PLAYUI, 6000);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mMediaPlayer.isPlaying()) {
			mMediaPlayer.pause();
		}
		mMediaPlayerState = STATE_PAUSED;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mHandler.removeMessages(SHOW_PROGRESS);
		Stop();
	}

	private boolean isInPlaybackState() {
		return (mMediaPlayer != null &&
				mMediaPlayerState != STATE_ERROR &&
				mMediaPlayerState != STATE_IDLE &&
				mMediaPlayerState != STATE_PREPARING);
	}

	/*
	 * 这里可拿到当前加载视频文件的分辨率 1280x720 1920x1080 ..
	 * 通过视频分辨率给播放界面定大小
	 */
	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		mVideoWidth = mp.getVideoWidth();
		mVideoHeight = mp.getVideoHeight();
		Log.i(TAG, "width: "+width+"  height: "+height);
		if (mVideoHeight != 0 && mVideoWidth != 0) {
			mSurfaceView.getHolder().setFixedSize(mVideoWidth, mVideoHeight);
			mSurfaceView.requestLayout();
		}
	}

	/*
	 * 视频进度条
	 */
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		if(fromUser) {
			mSeekTo(progress);
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {

	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		/*
		 * 底部控制栏的显示与隐藏逻辑
		 */
		if (mPlayui.getVisibility() != View.VISIBLE) { 
			mHandler.sendEmptyMessage(VISIBLE_PLAYUI); 
		}else{
			mHandler.removeMessages(GONE_PLAYUI); 
			mHandler.sendEmptyMessage(VISIBLE_PLAYUI);
		}
		return gd.onTouchEvent(event);
	}

	/*
	 * 手势动作
	 */
	@SuppressWarnings("deprecation")
	private GestureDetector gd = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
		@Override
		public boolean onDoubleTap(MotionEvent e) {
			/*
			 * 双击
			 */
			if (isPlaying()) {
				mPause();
			}else {
				mStart();
			}
			return true;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			/*
			 * 滑动
			 */
			float x = e2.getX() - e1.getX();
			float y = e2.getY() - e1.getY();
			if(x > 100) {
				mFastforward();
				return true;
			} else if(x < -100) {
				mRewind();
				return true;
			}
			Log.i("md", "y: "+y);
			if(y > 100) {
				mAddLightness();
				return true;
			} else if(y < -100) {
				mLessLightness();
				return true;
			}
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			/*
			 * 按下
			 */
			return super.onDown(e);
		}
	});

	/*
	 *  获取当前亮度
	 */
	public static int GetLightness(Activity act){
		return System.getInt(act.getContentResolver(),System.SCREEN_BRIGHTNESS,-1);
	}

	/*
	 *  设置亮度
	 */
	public static void SetLightness(Activity act,int value)
	{        
		try {
			System.putInt(act.getContentResolver(),System.SCREEN_BRIGHTNESS,value); 
			WindowManager.LayoutParams lp = act.getWindow().getAttributes(); 
			lp.screenBrightness = (value<=0?1:value) / 255f;
			act.getWindow().setAttributes(lp);
		} catch (Exception e) {
			Log.i(TAG, ""+e.toString());
		}        
	}

	/*
	 * 增加亮度
	 */
	private void mAddLightness(){
		int light = GetLightness(this);
		light += 25;
		if (light>0 && light <255) {
			SetLightness(this,light);
		}
	}

	/*
	 * 减少亮度
	 */
	private void mLessLightness(){
		int light = GetLightness(this);
		light -= 25;
		if (light>0 && light <255) {
			SetLightness(this,light);
		}
	}
}
