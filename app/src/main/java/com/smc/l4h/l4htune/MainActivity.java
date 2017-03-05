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

        // keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
