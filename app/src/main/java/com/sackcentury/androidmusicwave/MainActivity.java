package com.sackcentury.androidmusicwave;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;

import java.io.IOException;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements MediaPlayer.OnPreparedListener {

    Visualizer visualizer;
    VisualizerView visualizerView;
    MediaPlayer mediaPlayer;
    Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.wrapper);
        final Button button = (Button) findViewById(R.id.btn_play);
        mediaPlayer = new MediaPlayer();

//        visualizerView = (VisualizerView) findViewById(R.id.wavView);

        initVisual(relativeLayout);


        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                        }
                    }

                    mediaPlayer.reset();
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    try {
                        mediaPlayer.setDataSource("http://www.tingge123.com/mp3/2015-01-29/1422490375.mp3");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mediaPlayer.prepareAsync();
                    mediaPlayer.setOnPreparedListener(MainActivity.this);
                    mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                        @Override
                        public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {
                            button.setText("Buffering... ---> " + i + "%");
                        }
                    });


                }
            });
        }


    }

    private void initVisual(RelativeLayout relativeLayout) {
        visualizerView = new VisualizerView(this);
        visualizerView.init();
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800);
        visualizerView.setLayoutParams(layoutParams);
        relativeLayout.addView(visualizerView);
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        mediaPlayer.start();
        visualizer = new Visualizer(mediaPlayer.getAudioSessionId());
        visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[0]*2);
        visualizer.setDataCaptureListener(
                new Visualizer.OnDataCaptureListener() {
                    public void onWaveFormDataCapture(Visualizer visualizer,
                                                      byte[] bytes, int samplingRate) {
                        visualizerView.updateVisualizer(bytes);
                    }

                    public void onFftDataCapture(Visualizer visualizer,
                                                 byte[] fft, int samplingRate) {
                        visualizerView.updateVisualizer(fft);
                    }
                }, Visualizer.getMaxCaptureRate()/2, false, true);
        visualizer.setEnabled(true);
    }


    class VisualizerView extends View {
        private byte[] mBytes;
        private float[] mPoints;
        private Rect mRect = new Rect();

        private Paint mForePaint = new Paint();
        private int mSpectrumNum = 48;
        private boolean mFirst = true;

        public VisualizerView(Context context) {
            super(context);
            init();
        }

        private void init() {
            mBytes = null;

            mForePaint.setStrokeWidth(10f);
            mForePaint.setAntiAlias(true);
            int color = Color.rgb(random.nextInt(255), random.nextInt(255), random.nextInt(255));
            mForePaint.setColor(color);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        if (mPoints != null) {
                            for (int i = 0; i < mSpectrumNum; i++) {
                                float height = mPoints[i * 4 + 3] + 2;
                                if (height < getHeight() - 100) {
                                    mPoints[i * 4 + 3] = height;
                                }
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    invalidate();
                                }
                            });
                        }
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }).start();
        }


        public void updateVisualizer(byte[] fft) {
            if (mFirst) {
                mFirst = false;
            }
            byte[] model = new byte[fft.length / 2 + 1];
            model[0] = (byte) Math.abs(fft[1]);
            for (int i = 2, j = 1; j < mSpectrumNum; ) {
                model[j] = (byte) Math.hypot(fft[i], fft[i + 1]);
                i += 2;
                j++;
            }
            mBytes = model;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (mBytes == null) {
                return;
            }

            if (mPoints == null || mPoints.length < mBytes.length * 4) {
                mPoints = new float[mBytes.length * 4];
            }

            mRect.set(0, 0, getWidth(), getHeight());

//            //绘制波形
//            for (int i = 0; i < mBytes.length - 1; i++) {
//                mPoints[i * 4] = mRect.width() * i / (mBytes.length - 1);
//                mPoints[i * 4 + 1] = mRect.height() / 2
//                        + ((byte) (mBytes[i] + 128)) * (mRect.height() / 2) / 128;
//                mPoints[i * 4 + 2] = mRect.width() * (i + 1) / (mBytes.length - 1);
//                mPoints[i * 4 + 3] = mRect.height() / 2
//                        + ((byte) (mBytes[i + 1] + 128)) * (mRect.height() / 2) / 128;

//            }

            //绘制频谱
            final int baseX = mRect.width() / mSpectrumNum;
            final int height = mRect.height();

            for (int i = 0; i < mSpectrumNum; i++) {
                if (mBytes[i] < 0) {
                    mBytes[i] = 127;
                }

                final int xi = baseX * i + baseX / 2;

                mPoints[i * 4] = xi;
                mPoints[i * 4 + 1] = height;

                mPoints[i * 4 + 2] = xi;
//                mPoints[i * 4 + 3] = height - (mBytes[i] * 2 > height ? height : mBytes[i] * 2);
//                mPoints[i * 4 + 3] = height - (mBytes[i] > 10 ? mBytes[i] * 2 : 1);
                mPoints[i * 4 + 3] = height - mBytes[i];

            }

            canvas.drawLines(mPoints, mForePaint);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (isFinishing() && mediaPlayer != null) {
            visualizer.release();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

}
