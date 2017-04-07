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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager senSensorManager;
    private Sensor senAccelerometer;

    private volatile float accY, accX;
    private Thread audioThread;
    private AudioTrack buttonSoundFXTrack;

    private TextView scaleIndicatorTextView;


    private volatile boolean isPlayingNote = false;

    int[] diatonicLookupTable;
    int[] c5MajorScale = {60, 62, 64, 65, 67, 69, 71, 72};
    int[] e6MinorScale = {76, 78, 79, 81, 83, 84, 86, 88};
    int[] c6MajorScale = {72, 74, 76, 77, 79, 81, 83, 84};
    int[] e5MinorScale = {64, 66, 67, 69, 71, 72, 74, 76};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initScaleChangeSoundFX();

        senSensorManager = (SensorManager) getSystemService(this.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //use the onTouchEvent to control the play and stop for the continuous tone.
        View thisActivity = findViewById(R.id.activity_main);
        if(thisActivity != null){
            thisActivity.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    switch(motionEvent.getAction()){
                        case MotionEvent.ACTION_DOWN:
                            isPlayingNote = true;
                            return true;
                        case MotionEvent.ACTION_UP:
                            isPlayingNote = false;
                            return true;
                        default:
                            return false;
                    }
                }
            });
        }

        diatonicLookupTable = c5MajorScale;
        scaleIndicatorTextView = (TextView) findViewById(R.id.ScaleIndicator);
        scaleIndicatorTextView.setText("C5 Major");
    }

    int scaleRotator = 1;
    //region "PlaySomething" button
    public void onClick(View v) {
        Log.e("playButton", "clicked");
        buttonSoundFXTrack.stop();
        buttonSoundFXTrack.setPlaybackHeadPosition(0);
        buttonSoundFXTrack.play();

        switch(scaleRotator){
            case 0:
                diatonicLookupTable = c5MajorScale;
                scaleIndicatorTextView.setText("C5 Major");
                break;
            case 1:
                diatonicLookupTable = e6MinorScale;
                scaleIndicatorTextView.setText("E6 Minor");
                break;
            case 2:
                diatonicLookupTable = c6MajorScale;
                scaleIndicatorTextView.setText("C6 Major");
                break;
            case 3:
                diatonicLookupTable = e5MinorScale;
                scaleIndicatorTextView.setText("E5 Minor");
                break;
            default:
                diatonicLookupTable = c5MajorScale;
        }
        scaleRotator = (scaleRotator + 1) % 4;
    }

    private void initScaleChangeSoundFX() {
        short[] snare = makeACuteSound();

        buttonSoundFXTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                snare.length, AudioTrack.MODE_STATIC);
        buttonSoundFXTrack.write(snare, 0, snare.length);
    }

    private short[] makeACuteSound(){
        double currentAngle = 0;
        double lastFreq = 500;
        double freqStep = 1500.0 / 10000;
        double formulaConstant = 2.0*Math.PI / 44100;
        double mSound;
        short[] mBuffer = new short[10000];
        for (int i = 0; i < mBuffer.length; i++) {
            mSound = Math.sin(currentAngle);
            mBuffer[i] = (short) (mSound*Short.MAX_VALUE * 0.2);

            currentAngle += formulaConstant * lastFreq;
            lastFreq += freqStep;
        }

        return mBuffer;
    }


    public void octaveUp(View v){
        if(diatonicLookupTable[diatonicLookupTable.length-1] + 12 <= 127){
            int[] temp = Arrays.copyOf(diatonicLookupTable, diatonicLookupTable.length);
            for(int i = 0; i < temp.length; i++){
                temp[i] += 12;
            }
            diatonicLookupTable = temp;
        } else {
            Toast.makeText(this, "Already at highest possible octave", Toast.LENGTH_SHORT).show();
        }
    }

    public void octaveDown(View v){
        if(diatonicLookupTable[0] - 12 >= 0){
            int[] temp = Arrays.copyOf(diatonicLookupTable, diatonicLookupTable.length);
            for(int i = 0; i < temp.length; i++){
                temp[i] -= 12;
            }
            diatonicLookupTable = temp;
        } else {
            Toast.makeText(this, "Already at lowest possible octave", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        accX = sensorEvent.values[0];
        accY = sensorEvent.values[1];
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
        try {
            audioThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            Log.e("in onPause", "thread joined");
        }
    }

    class SineWaveGenerator implements Runnable{
        AudioTrack audioTrack;
        short[] zeroBuffer; // this will be a buffer of 0s to help eliminate the trailing clicking sound when a playback ends.

        @Override
        public void run() {
            initAudioTrack();

            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            //get the note lists
            double[] midi_notes = new  double[128];
            for(int i = 0; i < 128; i++){
                midi_notes[i] = 440 * Math.pow(2, (i-69)/12.0);
            }

            //theses boundaries divide possible accY readings in to 8 regions, corresponding to 8 notes
            double[] yAxisBoundaries = {-7.928366545, -5.760295472, -3.028366545, 0, 3.028366545, 5.760295472, 7.928366545};

            //these values are used for volume computation. Max volume is output when the magnitude
            //of accX reading is equal or greater than the xMaxValue, for the corresponding note.
            //There are 8 values in this array, corresponding to the 8 notes.
            double[] xMaxValues = {4.449106897, 6.929646456, 8.731863937, 9.679345738, 9.679345738, 8.731863937, 6.929646456, 4.449106897};

            int bufferSize = 441;
            int delayOfFreqChangeInSamples = 1000; // 44100 will be 1 sec delay
            double formulaConstant = 2.0*Math.PI / 44100;

            double currentAngle = 0;
            double lastFreq = 0;

            int delayOfAmplitudeChangeInSamples = 1000;
            double currentAmplitudeCoefficient = 1;

            short[] mBuffer = new short[bufferSize];

            boolean isRecoveringFromStop = true;

            while(!Thread.currentThread().isInterrupted()){
                if(!isPlayingNote){
                    if(!isRecoveringFromStop){
                        // We write to the AudioTrack some trailing samples to eliminate the potential clicking sound
                        // at the end of a playback.
                        double amplitudeCoefficientStep = (0 - currentAmplitudeCoefficient) / bufferSize;
                        double mSound;
                        for (int i = 0; i < mBuffer.length; i++) {
                            mSound = Math.sin(currentAngle);
                            mBuffer[i] = (short) (mSound*Short.MAX_VALUE * currentAmplitudeCoefficient);
                            currentAngle += formulaConstant * lastFreq;
                            currentAmplitudeCoefficient += amplitudeCoefficientStep;
                        }
                        audioTrack.write(mBuffer, 0, mBuffer.length);
                        audioTrack.write(zeroBuffer, 0, zeroBuffer.length);

                        // Pause and Flush makes sure there's no clicking sound at the beginning
                        // of the next round of playback. All residual samples from the last playback
                        // are flushed.
                        audioTrack.pause();
                        audioTrack.flush();
                        isRecoveringFromStop = true;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Log.e("in SineWaveGenerato", "interrupted in sleep, exiting while loop");
                        break; // break from while loop
                    }
                }else{
                    if(isRecoveringFromStop){
                        currentAngle = 0;
                        currentAmplitudeCoefficient = 0;
                        isRecoveringFromStop = false;

                        // for some reason that's yet to be figured out, a call to play() is needed
                        // to avoid some minor error of restarting of the AudioTrack.
                        audioTrack.play();
                    }
                    // determine which note to produce
                    float currentY = accY;

                    int low = 0, high = yAxisBoundaries.length - 1;
                    while (high >= low){ // binary search for the suitable range.
                        int middle = (low + high) / 2;
                        if(yAxisBoundaries[middle] < currentY){
                            low = middle + 1;
                        }else{
                            high = middle - 1;
                        }
                    }
                    int noteIndex = high > low ? high : low;
                    double freqStep =
                            (midi_notes[diatonicLookupTable[noteIndex]] - lastFreq)
                            / delayOfFreqChangeInSamples;

                    float currentX = Math.abs(accX);
                    double xMax = xMaxValues[noteIndex];
                    // the amplitude is modulated in the range of -30dBFS to 0 dBFS
                    double targetAmplitudeCoefficient = Math.pow(10, -1.5 * currentX / xMax);
                    double amplitudeCoefficientStep =
                            (targetAmplitudeCoefficient - currentAmplitudeCoefficient)
                            / delayOfAmplitudeChangeInSamples;

                    double mSound;

                    for (int i = 0; i < mBuffer.length; i++) {
                        mSound = Math.sin(currentAngle);
                        mBuffer[i] = (short) (mSound*Short.MAX_VALUE * currentAmplitudeCoefficient);

                        currentAngle += formulaConstant * lastFreq;
                        lastFreq += freqStep;

                        currentAmplitudeCoefficient += amplitudeCoefficientStep;
                    }

                    audioTrack.write(mBuffer, 0, mBuffer.length);

                }
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
            zeroBuffer = new short[mBufferSize]; //as guaranteed by Java standard, new arrays of basic types will have initial values of 0

            Log.e("from initAudioTrack()", "the minBufferSize is " + mBufferSize);

            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    mBufferSize, AudioTrack.MODE_STREAM);

            audioTrack.play();
        }
    }
}
