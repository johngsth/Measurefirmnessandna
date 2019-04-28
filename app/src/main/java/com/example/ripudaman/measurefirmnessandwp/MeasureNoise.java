package com.example.ripudaman.measurefirmnessandwp;

import android.Manifest.permission;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MeasureNoise extends AppCompatActivity {
    TextView mStatusView;
    private MediaRecorder mRecorder;
    Thread runner;
    public static double soundinDb;
    private static double mEMA = 0.0;
    static final private double EMA_FILTER = 0.6;
    private static final int PERMISSION_REQUEST_CODE = 1;
    private Button record;
    Context mContext;
    // Constructor
    public MeasureNoise(){}
    public MeasureNoise(Context contextFromActivity)
    {
        mContext= contextFromActivity;
    }

    final Runnable updater = new Runnable(){

        public void run(){
            updateTv();
        };
    };
    final Handler mHandler = new Handler();

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    0);

        } else {
            startRecorder();
        }*/

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {

            if (checkSelfPermission(permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_DENIED) {

                Log.d("permission", "permission denied to save file - requesting it");
                String[] permissions = {permission.RECORD_AUDIO};

                requestPermissions(permissions, PERMISSION_REQUEST_CODE);

            }
        }

        setContentView(R.layout.activity_measure_noise);
        mStatusView = (TextView) findViewById(R.id.labelAcc);
        record = findViewById(R.id.button3);

        record.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                goToRecordActivity();
            }
        });

        //mStatusView.setEnabled(false);

        if (runner == null)
        {
            runner = new Thread(){
                public void run()
                {
                    while (runner != null)
                    {
                        try
                        {
                            Thread.sleep(1000);
                            Log.i("Noise", "Tock");
                        } catch (InterruptedException e) { };
                        mHandler.post(updater);
                    }
                }
            };
            runner.start();
            Log.d("Noise", "start runner()");
        }
    }

    private void goToRecordActivity(){
        Intent intent = new Intent(this, NoiseSetup.class);

        startActivity(intent);
    }

    public void startRecorder(){
        if (mRecorder == null)
        {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setAudioChannels(1);
            mRecorder.setAudioSamplingRate(8000);
            mRecorder.setAudioEncodingBitRate(44100);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setOutputFile("dev/null");
            if(mRecorder != null)
            {
                Log.d("Debug","Record sound method");
                try
                {
                    mRecorder.prepare();
                }catch (java.io.IOException ioe) {
                    android.util.Log.e("[Monkey]", "IOException: " + android.util.Log.getStackTraceString(ioe));

                }catch (java.lang.SecurityException e) {
                    android.util.Log.e("[Monkey]", "SecurityException: " + android.util.Log.getStackTraceString(e));
                }
                try
                {
                    mRecorder.start();
                }catch (java.lang.SecurityException e) {
                    android.util.Log.e("[Monkey]", "SecurityException: " + android.util.Log.getStackTraceString(e));
                }
            }
            //mEMA = 0.0;
        }

    }

    public void onResume()
    {
        super.onResume();
        startRecorder();
    }

    public void onPause()
    {
        super.onPause();
        stopRecorder();
    }

    public void stopRecorder() {
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
    }

    public void updateTv(){
        //mStatusView.setText(Double.toString(getAmplitudeEMA()) + " dB");
        mStatusView.setText(Double.toString(soundDb(1)) + " dB");
        //soundinDb = soundDb(32767);
        //Log.d("Debug"," Sound in Db = " + soundinDb);
        //mStatusView.setText(Double.toString(soundinDb) + " dB");
    }
    public double soundDb(double ampl){
        return  20 * Math.log10(getAmplitudeEMA()/ampl);
    }
    public double getAmplitude() {
        if (mRecorder != null)
            return  (mRecorder.getMaxAmplitude());
        else
            return 0;
    }
    public double getAmplitudeEMA() {
        double amp =  getAmplitude();
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA; //filtering y(n) = a*x + b*y(n-1)
        return mEMA;
    }
}

