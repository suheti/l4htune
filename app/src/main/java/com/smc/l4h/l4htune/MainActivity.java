package com.smc.l4h.l4htune;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager senSensorManager;
    private Sensor senAccelerometer;
    private long lastUpdate;

    private short[] c4, e4, g4, c5;

    private volatile float accZ;
    private Thread audioThread;
    private SineWaveGenerator audioRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initAudioTrack();

        senSensorManager = (SensorManager) getSystemService(this.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    //region "PlaySomething" button
    public void onClick(View v){
        Log.e("something", "something else");

        playBuffer(e4);
        playBuffer(e4);
        playBuffer(e4);
    }

    private AudioTrack mAudioTrack;

    private void initAudioTrack() {
        // AudioTrack definition
        int mBufferSize = AudioTrack.getMinBufferSize(44100,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_8BIT);

        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                mBufferSize, AudioTrack.MODE_STREAM);

        mAudioTrack.play();

        // initialise note buffers.
        c4 = getBuffer(261, 44100);
        e4 = getBuffer(329, 44100);
        g4 = getBuffer(392, 44100);
        c5 = getBuffer(523, 44100);

    }

    private void playBuffer(short[] input){
        mAudioTrack.write(input, 0, input.length);
    }

    private short[] getBuffer(double frequency, int duration){
        double mSound;
        short[] mBuffer = new short[duration];
        for (int i = 0; i < mBuffer.length; i++) {
            mSound = Math.sin((2.0*Math.PI * i/(44100/frequency)));
            mBuffer[i] = (short) (mSound*Short.MAX_VALUE);
        }
        return mBuffer;
    }

    //endregion "PlaySomething" button

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        accZ = sensorEvent.values[2];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        senSensorManager.registerListener(this, senAccelerometer , SensorManager.SENSOR_DELAY_NORMAL);

        audioRunnable = new SineWaveGenerator();
        audioThread = new Thread(audioRunnable);
        audioThread.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        senSensorManager.unregisterListener(this);

        audioThread.interrupt();
    }

    class SineWaveGenerator implements Runnable{
        private AudioTrack audioTrack;

        @Override
        public void run() {
            Log.e("in run", "" + accZ);
            initAudioTrack();

            float lastAccZ = 0;
            int bufferSize = 22050;

            while(!Thread.currentThread().isInterrupted()){
                float zStep = (accZ - lastAccZ) / bufferSize;

                double mSound;
                short[] mBuffer = new short[bufferSize];
                for (int i = 0; i < mBuffer.length; i++) {
                    lastAccZ += zStep;
                    mSound = Math.sin(2.0*Math.PI * (800*(lastAccZ+10.0)/20 + 200) * i/44100.0);
                    mBuffer[i] = (short) (mSound*Short.MAX_VALUE);
                }

                audioTrack.write(mBuffer, 0, mBuffer.length); //This is a blocking call, returns at near the end of playback of the current buffer.

            }

            //release the AudioTrack
            audioTrack.pause();
            audioTrack.flush();
            audioTrack.release();
        }

        private void initAudioTrack(){
            int mBufferSize = AudioTrack.getMinBufferSize(44100,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_8BIT);
            Log.e("in run", "" + mBufferSize);
            Log.e("in run acc delay", ""+SensorManager.SENSOR_DELAY_NORMAL);

            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    mBufferSize, AudioTrack.MODE_STREAM);

            audioTrack.play();
        }

        private void playTune(){
            double mSound;
            short[] mBuffer = new short[4410];
            for (int i = 0; i < mBuffer.length; i++) {
                mSound = Math.sin((2.0*Math.PI * i/(44100/(2000*accZ/10 + 200))));
                mBuffer[i] = (short) (mSound*Short.MAX_VALUE);
            }
            audioTrack.write(mBuffer, 0, mBuffer.length);
        }
    }
}
