package com.example.ripudaman.measurefirmnessandwp;

import android.Manifest.permission;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.text.DecimalFormat;

public class MeasureNoise extends AppCompatActivity {
    TextView currentValue, maxValue, minValue, avgValue;
    private MediaRecorder mRecorder;
    Thread runner;
    public static double soundinDb;
    private static double mEMA = 0.0;
    static final private double EMA_FILTER = 0.6;
    private static final int PERMISSION_REQUEST_CODE = 1;
    private Button record;
    Context mContext;

    DecimalFormat df1 = new DecimalFormat("####.0");

    // Constructor
    public MeasureNoise() {
    }

    public MeasureNoise(Context contextFromActivity) {
        mContext = contextFromActivity;
    }

    //testing new way
    public static float dbCount = 40;
    public static float minDB = 100;
    public static float maxDB = 0;
    public static float lastDbCount = dbCount;
    private static float min = 0.5f;  //Set the minimum sound change
    private static float value = 0;   // Sound decibel value
    float volume = 10000;
    boolean refreshed = false;

    private GraphView graph;
    private PointsGraphSeries<DataPoint> maxChangeSeries;
    private PointsGraphSeries<DataPoint> printdBValues;
    private int graphCount;

    final Runnable updater = new Runnable() {

        public void run() {
            updateText();
            graphData();
        }

        ;
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
        currentValue = (TextView) findViewById(R.id.current);
        maxValue = (TextView) findViewById(R.id.max);
        minValue = (TextView) findViewById(R.id.min);
        avgValue = (TextView) findViewById(R.id.avg);
        record = findViewById(R.id.button3);
        //FloatingActionButton refreshbut = (FloatingActionButton)findViewById(R.id.refresh);

        record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToRecordActivity();
            }
        });

        /*refreshbut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshed=true;
                MeasureNoise.minDB=100;
                MeasureNoise.dbCount=0;
                MeasureNoise.lastDbCount=0;
                MeasureNoise.maxDB=0;
            }
        });*/

        if (runner == null) {
            runner = new Thread() {
                public void run() {
                    while (runner != null) {
                        try {
                            Thread.sleep(1000);
                            Log.i("Noise", "Tock");
                        } catch (InterruptedException e) {
                        }
                        catch (NullPointerException e){
                            e.printStackTrace();
                        }
                        mHandler.post(updater);
                    }
                }
            };
            runner.start();
            Log.d("Noise", "start runner()");
        }

        initializeGraph();
    }

    private void goToRecordActivity() {
        Intent intent = new Intent(this, NoiseSetup.class);

        startActivity(intent);
    }

    public void startRecorder() {
        if (mRecorder == null) {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setAudioChannels(1);
            mRecorder.setAudioSamplingRate(8000);
            mRecorder.setAudioEncodingBitRate(44100);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setOutputFile("dev/null");
            if (mRecorder != null) {
                Log.d("Debug", "Get dB reading method");
                try {
                    mRecorder.prepare();
                } catch (java.io.IOException ioe) {
                    android.util.Log.e("[Exception]", "IOException: " + android.util.Log.getStackTraceString(ioe));

                } catch (java.lang.SecurityException e) {
                    android.util.Log.e("[Exception]", "SecurityException: " + android.util.Log.getStackTraceString(e));
                }
                try {
                    mRecorder.start();
                } catch (java.lang.SecurityException e) {
                    android.util.Log.e("[Exception]", "SecurityException: " + android.util.Log.getStackTraceString(e));
                }
            }
        }

    }

    public void onResume() {
        super.onResume();
        startRecorder();
    }

    public void onPause() {
        super.onPause();
        //stopRecorder();
    }

    public void stopRecorder() {
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
    }

    public float getAmplitude(){
        if (mRecorder != null) {
            try {
                return mRecorder.getMaxAmplitude();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                return 0;
            }
        } else {
            return 5;
        }
    }

    public void updateText() {
        //currentValue.setText(Double.toString(getAmplitudeEMA()) + " dB");
        //currentValue.setText(Double.toString(soundDb(1)) + " dB");
        //soundinDb = soundDb(32767);
        //Log.d("Debug"," Sound in Db = " + soundinDb);
        //currentValue.setText(Double.toString(soundinDb) + " dB");
        volume = mRecorder.getMaxAmplitude();  //Get the sound pressure value
        if (volume > 0 && volume < 1000000) {
            MeasureNoise.setDbCount(20 * (float) (Math.log10(volume)));  //Change the sound pressure value to the decibel value
            // Update with thread
            Message message = new Message();
            message.what = 1;
            mHandler.sendMessage(message);
        }
        currentValue.setText(df1.format(MeasureNoise.dbCount));
        minValue.setText(df1.format(MeasureNoise.minDB));
        avgValue.setText(df1.format((MeasureNoise.minDB + MeasureNoise.maxDB)/2));
        maxValue.setText(df1.format(MeasureNoise.maxDB));
    }

    /*public double soundDb(double ampl){
        return  20 * Math.log10(getAmplitude());
    }
    public double getAmplitude() {
        if (mRecorder != null){
            try {
                return mRecorder.getMaxAmplitude();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                return 0;
            }
        }
        else
            return 5;
    }

    public double getAmplitudeEMA() {
        double amp =  getAmplitude();
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA; //filtering y(n) = a*x + b*y(n-1)
        return mEMA;
    }*/

    public static void setDbCount (float dbValue){
        if (dbValue > lastDbCount) {
            value = dbValue - lastDbCount > min ? dbValue - lastDbCount : min;
        } else {
            value = dbValue - lastDbCount < -min ? dbValue - lastDbCount : -min;
        }
        dbCount = lastDbCount + value * 0.2f; //To prevent the sound from changing too fast
        lastDbCount = dbCount;
        if (dbCount < minDB) minDB = dbCount;
        if (dbCount > maxDB) maxDB = dbCount;
    }

    public void initializeGraph(){
        graph = (GraphView) findViewById(R.id.graph);

        GridLabelRenderer gridLabel = graph.getGridLabelRenderer();
        gridLabel.setHorizontalAxisTitle("Time");
        gridLabel.setVerticalAxisTitle("dB reading");

        graph.setTitle("dB per Second/Time");
        graph.getViewport().setScrollableY(true);
        graph.getViewport().scrollToEnd();
        graph.getViewport().setBorderColor(Color.WHITE);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMaxX(30);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMaxY(90.0);
    }

    public void graphData(){

        if (volume != 0){
            graphCount++;

            maxChangeSeries = new PointsGraphSeries<>(new DataPoint[]{
                    new DataPoint(graphCount, volume)
            });
            maxChangeSeries.setColor(Color.RED);
            graph.addSeries(maxChangeSeries);

            printdBValues = new PointsGraphSeries<>(new DataPoint[]{
                    new DataPoint(graphCount, dbCount)
            });
            printdBValues.setColor(Color.RED);
            graph.addSeries(printdBValues);
        }
    }
}