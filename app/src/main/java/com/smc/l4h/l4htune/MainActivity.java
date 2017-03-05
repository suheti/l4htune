package com.smc.l4h.l4htune;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import java.util.Random;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager senSensorManager;
    private Sensor senAccelerometer;
    private long lastUpdate;

    private short[] snare, e4, g4, c5;

    private volatile float accZ;
    private Thread audioThread;
    private AudioTrack snareTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initPercussion();

        senSensorManager = (SensorManager) getSystemService(this.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    //region "PlaySomething" button
    public void onClick(View v){
        Log.e("playButton", "clicked");
        snareTrack.pause();
        snareTrack.reloadStaticData();
        snareTrack.play();
    }

    private void initPercussion() {
        short[] snare = makeSnare();

        snareTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                snare.length, AudioTrack.MODE_STATIC);
        snareTrack.write(snare, 0, snare.length);
    }

    private short[] makeSnare(){
        double mSound;
        Random rand = new Random();
        short[] mBuffer = new short[10000];
        for (int i = 0; i < mBuffer.length; i++) {
            mSound = 2 * Math.PI * 250 * i / 44100 * (1 - i/44100.0) * 0.2;
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

        audioThread = new Thread(new SineWaveGenerator());
        audioThread.start(); // we can comment out this line to disable the continuous playback of varying pitch
    }

    @Override
    protected void onPause() {
        super.onPause();
        senSensorManager.unregisterListener(this);

        audioThread.interrupt();
    }

    class SineWaveGenerator implements Runnable{
        AudioTrack audioTrack;

        @Override
        public void run() {
            initAudioTrack();

            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            double lastAccZ = 0;
            int bufferSize = 441;
            int delayOfChangeInSamples = 11012; // 44100 will be 1 sec delay
            double formulaConstant = 2.0*Math.PI / 44100;

            double currentAngle = 0;

            while(!Thread.currentThread().isInterrupted()){
                // update the axis value used for calculation gradually
                // hence the frequency changes gradually
                double zStep = (accZ - lastAccZ) / delayOfChangeInSamples;

                double mSound;
                short[] mBuffer = new short[bufferSize];
                for (int i = 0; i < mBuffer.length; i++) {

                    mSound = Math.sin(currentAngle);
                    mBuffer[i] = (short) (mSound*Short.MAX_VALUE);

                    /* freq range here is roughly 200~1000Hz
                     * axix reading from accelerometer is -9.8 ~ +9.8 m/s^2 */
                    currentAngle += formulaConstant * (40*(lastAccZ + 10) + 200);
                    lastAccZ += zStep;
                }

                audioTrack.write(mBuffer, 0, mBuffer.length);
            }

            //release the AudioTrack
            audioTrack.pause();
            audioTrack.flush();
            audioTrack.release();
        }

        private void initAudioTrack(){
            int mBufferSize = AudioTrack.getMinBufferSize(44100,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    mBufferSize, AudioTrack.MODE_STREAM);

            audioTrack.play();
        }
    }
}
