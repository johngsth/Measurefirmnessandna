package com.example.ripudaman.measurefirmnessandwp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.util.Measure;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.opencsv.CSVWriter;

import org.apache.commons.lang3.ObjectUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class MeasureFirmness extends AppCompatActivity implements SensorEventListener {

    public static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 6;

    private SensorManager sensorManager;
    private TextView timerText;
    private TextView maxAccText;
    private TextView currentAccText;
    private TextView currentForceText;
    private TextView maxForceText;
    private int count;
    private double beginTime;
    private double endTime;
    private double settleTime;
    private double maxAcc;
    private double maxAccTime;
    private double maxForce;
    private double maxChange;
    private double previousValue;
    private List<String[]> accData = new ArrayList<>();
    private List<String[]> forceData = new ArrayList<>();
    private Button startButton, resetButton, pauseButton;
    private Button measureFirmnessButton;
    private boolean wasClicked;
    private boolean begin;
    private boolean freeFallDetected;
    private boolean firstFreeFall;
    private boolean impactDetected;

    long MillisecondTime, StartTime, TimeBuff, UpdateTime = 0L;
    int Seconds, Minutes, MilliSeconds;

    Handler handler;
    Sensor accelerometer;
    Sensor gyroscope;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measure_firmness);

        count = 0;
        previousValue = -1.0;
        maxAcc = -1.0;
        maxForce = -1.0;

        wasClicked = false;
        begin = false;
        freeFallDetected = false;
        firstFreeFall = true;

        handler = new Handler();

        initializeTextViews();
        initializeToolbar();
        initializeButtons();
        initializeSensors();
    }

    @Override
    protected void onPause(){
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onResume(){
        super.onResume();
        sensorManager.registerListener(MeasureFirmness.this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(final SensorEvent sensorEvent) {
        synchronized (this){

            // Static time for the rest of this events calculations
            String second = String.valueOf(Seconds);
            String millis = String.valueOf(MilliSeconds);
            String currentTime = second + "." + millis;

            double x;
            double y;
            double z;
            double change;
            double currentForce;
            double resultantVector;

            // Filter to get only accelerometer data
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
                x = sensorEvent.values[0];
                y = sensorEvent.values[1];
                z = sensorEvent.values[2];

                // Get the average acceleration of this accelerometer event
                resultantVector = getResultantVector(x, y, z);

                // Get the current force on the phone calculated from this events average acceleration
                currentForce = getDynamicForce(resultantVector);

                // Get the change in acceleration compared to the last event
                change = Math.abs(resultantVector - previousValue);

                // Store the event data
                storeTestData(x, y, z, currentTime, resultantVector, currentForce);

                // Dynamically store the maximum values for acceleration, force, the change in acceleration, and the time of the respective maximum value
                saveMaxValues(resultantVector, currentForce, change, currentTime);

                // Save values and perform data analysis when the user starts recording their test
                if (wasClicked){

                    // Update texts now that we're recording
                    currentAccText.setText(String.format("%02f", resultantVector));
                    maxAccText.setText(String.format("%02f", maxAcc));
                    currentForceText.setText(String.format("%02f", currentForce));
                    maxForceText.setText(String.format("%02f", maxForce));


                    // If free fall is detected
                    if (freefallMonitor(resultantVector)){
                        freeFallDetected = true;

                        // Set the begin time of the free fall
                        // 0.15 accounts for the delay in detecting the free fall
                        if (begin){
                            beginTime = Double.valueOf(currentTime) + 0.15;
                            begin = false;

                            Log.d("begintime", String.valueOf(beginTime));
                        }

                        // Set the end of the first observed free fall in the test
                        // A bounce would probably trigger the detector and thus set the wrong end time
                        if (firstFreeFall){
                            endTime = Double.valueOf(currentTime);
                            firstFreeFall = false;
                            Log.d("endtime", String.valueOf(endTime));
                        }
                    }

                    // If a free fall was detected and an impact was detected, determine when the phone settles
                    if (freeFallDetected && impactDetected){

                        maxAccText.setText(String.valueOf(maxAcc));

                        // If there hasn't been much of a change with the last 20 data points,
                        // then the phone is done bouncing and its 'settle time' is set
                        if (change < 2.0){
                            count++;
                        }
                        else
                            count = 0;

                        if (count == 20){
                            settleTime = Double.valueOf(currentTime);

                            Log.d("settletime", String.valueOf(settleTime));
                        }
                    }
                    else { // Set test values and button text when not recording data

                    }

                    previousValue = resultantVector;

                    // Live feed of y-coordinate accelerometer values printed to logcat with the tag "VALUESHERE"
                    Log.d("VALUESHERE", String.valueOf(resultantVector));
                }
            }
        }
    }

    /**
     * Save recorded data to a .csv file and export it to the phones storage
     * When the user taps the floating action button*/
    public void saveOnClick(View view){
        CSVWriter writer;

        if (isExternalStorageWritable()){
            // Can save file
            File outputPath = getPublicDocumentStorageDir("exportFiles");
            File outputFile = new File(outputPath, "export.csv");

            try{
                // Check to see if file exists
                if (outputFile.exists() && !outputFile.isDirectory()){
                    FileWriter fileWriter = new FileWriter(outputFile, true);
                    writer = new CSVWriter(fileWriter);
                }
                else{
                    writer = new CSVWriter(new FileWriter(outputFile));
                }

                Log.d("FILEDEBUG", String.valueOf(outputFile));

                // Save data from list to csv file
                writer.writeAll(accData);
                writer.close();

                // Notify the user that the file saved successfully
                toastIt("File saved successfully!");

            }catch (Exception e){
                e.printStackTrace();
                Log.d("FILEDEBUG", String.valueOf(e));
                toastIt("Save failed");
            }
        }
        else{
            // Cannot save file, no external storage available
            toastIt("No external storage available!");
        }

        printList();
        printForceData();
    }

    /**
     * Utility method for making toasts*/
    public void toastIt(String message){
        Toast.makeText(MeasureFirmness.this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Runnable for the timer
     * Updates the timer text*/
    private Runnable runnable = new Runnable(){
        @Override
        public void run(){
            MillisecondTime = SystemClock.uptimeMillis() - StartTime;
            UpdateTime = TimeBuff + MillisecondTime;

            Seconds = (int) (UpdateTime / 1000);
            Minutes = Seconds / 60;
            Seconds = Seconds % 60;
            MilliSeconds = (int) (UpdateTime % 1000);

            timerText.setText("" + String.format("%02d",Minutes) + ":"
                    + String.format("%02d", Seconds) + ":"
                    + String.format("%03d", MilliSeconds));

            handler.postDelayed(this, 0);
        }
    };

    /**
     * Method for debugging the data list*/
    public void printList(){
        for (int i = 0; i < accData.size(); i++){
            Log.d("PRINTLIST", Arrays.toString(accData.get(i)));
        }
    }

    /**
     * Get mattress firmness rating
     * Called when the user presses pause or when the system stops recording automatically
     * Returns an integer rating on a scale of 1-10; 1 being the firmest, 10 being the softest*/
    public int getFirmnessRating(){

        int rating = 0;
        double freeFallDuration;
        double bounceDuration;
        double stoppageTime;
        double heightOfDrop;
        double impactSpeed;
        double impactForce;

        bounceDuration = getBounceDuration();
        freeFallDuration = getFreeFallDuration();
        heightOfDrop = calculateHeight(freeFallDuration);
        impactSpeed = calculateImpactSpeed(heightOfDrop);
        impactForce = calculateImpactForce(maxAcc);
        stoppageTime = endTime - maxAccTime;

        Log.i("RATING", "MAX VALUE: " + String.valueOf(maxAcc) +
                " | TIME OF PEAK: " + String.valueOf(maxAccTime) +
                " | BOUNCE DURATION: " + String.valueOf(bounceDuration) +
                " | FREE FALL DURATION: " + String.valueOf(freeFallDuration) +
                " | HEIGHT OF DROP: " + String.valueOf(heightOfDrop) +
                " | IMPACT SPEED: " + String.valueOf(impactSpeed) +
                " | IMPACT FORCE: " + String.valueOf(impactForce) +
                " | STOP TIME: " + String.valueOf(stoppageTime));

        if (freeFallDetected){

            measureFirmnessButton.setText(String.valueOf(rating));
        }
        else {
            measureFirmnessButton.setText("No Drop Detected");
        }

        // Determine the rating here

        return rating;
    }

    /**
     * Detects if the phone is in free fall by observing the root square of all three accelerometer vectors
     * If all three vectors approach zero, that means they are falling with gravity and thus, not feeling the gravity
     * Returns true while the phone is in free fall and data is being recorded, false otherwise.*/
    public boolean freefallMonitor(double resultantVector){

        if (resultantVector < 2.0){
            Log.d("FREEFALLING", "Free falling!");

            return true;
        }

        return false;
    }

    /**
     * Gets the bounce duration after initial impact of the phone with the test surface
     * settleTime is set when the the system doesn't detect a change greater than a certain threshold for more than 20 datapoints
     * saveTime is the time of the maximum acceleration (impact acceleration)*/
    public double getBounceDuration(){ return settleTime - maxAccTime; }

    /**
     * Function to calculate the duration of the phone's freefall
     * The values used for this calculation are determined by freeFallMonitor*/
    public double getFreeFallDuration(){
        return endTime - beginTime;
    }

    /**
     * Function to calcuate the height of the drop
     * Utilizes the phones drop duration for the calculation
     * The formula is: (1/2)*g*t^2   where t is the drop duration*/
    public double calculateHeight(double dropDuration){

        double height;
        height = 0.5 * 9.81 * Math.pow(dropDuration, 2);

        return height;
    }

    /**
     * Method to calculate the impact speed of the drop
     * Utilizes the height of the drop (ignoring air resistance)*/
    public double calculateImpactSpeed(double height){

        double impactSpeed;
        impactSpeed = Math.sqrt(2 * 9.81 * height);

        return impactSpeed;
    }

    /**
     * Method for calculating the force of impact of the drop
     * Utilizes Newtons second law, and the average smartphone weight
     * F = ma */
    public double calculateImpactForce(double maxAcceleration){
        double force;

        // Average smartphone weighs between 140 and 170 grams
        double mass = 0.160;

        // Newton's second law: F = ma
        force = mass * maxAcceleration;

        return force;
    }

    /**
     * Method for acquiring the resulting acceleration vector
     * The resultant vector is the root mean square of the three accelerometer values*/
    public double getResultantVector(double x, double y, double z){
        double vector;
        vector = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
        return vector;
    }

    /**
     * Returns the current force exerted by the current acceleration resultant vector
     * Assumes the mass of the phone to be 160g*/
    public double getDynamicForce(double vector){
        double currentForce = 0.160 * vector;

        //Log.d("DYNAMICFORCE", String.valueOf(currentForce));

        return currentForce;
    }

    /**
     * Prints the list of logged forces to logcat
     * The values are added dynamically along with the timestamp*/
    public void printForceData(){
        for (int i = 0; i < forceData.size(); i++){
            Log.d("FORCELIST", Arrays.toString(forceData.get(i)));
        }
    }

    public void checkPermissions(){
        if (ContextCompat.checkSelfPermission(MeasureFirmness.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MeasureFirmness.this,
                    Manifest.permission.READ_CONTACTS)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            }
            else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(MeasureFirmness.this,
                        new String[]{Manifest.permission.READ_CONTACTS},
                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);

                // MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
        else {
            // Permission already granted

            Log.d("PERMISSIONS", "Permission already granted");
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    /**
     *  Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public File getPublicDocumentStorageDir(String fileName) {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), fileName);
        if (!file.mkdirs()) {
            Log.e("FILEDEBUG", "Directory not created");
        }
        return file;
    }

    public void storeTestData(double x, double y, double z, String time, double resultant, double cForce){

        String[] accDataEntry;
        String[] currentForceDataEntry;

        // Stringify the values to insert into lists
        String xVal = String.valueOf(x);
        String yVal = String.valueOf(y);
        String zVal = String.valueOf(z);
        String resultantV = String.valueOf(resultant);
        String currentForce = String.valueOf(cForce);

        // Enter accelerometer data into a list
        accDataEntry = new String[]{time, xVal, yVal, zVal, resultantV};
        accData.add(accDataEntry);

        // Add the force for this event to the force data list
        currentForceDataEntry = new String[]{time, currentForce};
        forceData.add(currentForceDataEntry);
    }

    public void saveMaxValues(double acc, double force, double change, String time){
        // Save the maximum resultant acceleration value
        if (acc > maxAcc){
            maxAcc = acc;
            maxAccTime = Double.valueOf(time);

            if (freeFallDetected && (maxAcc > 14.0)){
                impactDetected = true;
            }

            Log.d("savedmax", String.valueOf(maxAcc) + " | " + String.valueOf(maxAccTime));
        }

        // Save the maximum observed force
        if (force > maxForce)
        {
            maxForce = force;

            Log.d("MAXFORCE", String.valueOf(maxForce));
        }

        // Save the maximum rate of change
        if (change > maxChange){
            maxChange = change;

            Log.d("MAXCHANGE", String.valueOf(maxChange));
        }
    }

    public void resetData(){
        // Reset time
        MillisecondTime = 0L;
        StartTime = 0L;
        TimeBuff = 0L;
        UpdateTime = 0L;
        Seconds = 0;
        Minutes = 0;
        MilliSeconds = 0;

        wasClicked = false;
        freeFallDetected = false;
        impactDetected = false;
        firstFreeFall = true;

        // Reset text values
        timerText.setText("00:00:00");
        currentAccText.setText("0.000000");
        maxAccText.setText("0.000000");
        currentForceText.setText("0.000000");
        maxForceText.setText("0.000000");
        measureFirmnessButton.setText("FIRMNESS RATING");

        // Reset variables
        count = 0;
        previousValue = -1.0;
        maxAcc = -1.0;
        maxForce = -1.0;
        maxChange = -1.0;

        // Clear the entries in the data list and list of forces for a new recording
        accData.clear();
        forceData.clear();

        toastIt("RECORDING RESET");
    }

    public void initializeTextViews(){
        timerText = findViewById(R.id.timer_text);
        currentAccText = findViewById(R.id.current_acc_value);
        maxAccText = findViewById(R.id.max_acc_value);
        currentForceText = findViewById(R.id.current_force_value);
        maxForceText = findViewById(R.id.max_force_value);
    }

    public void initializeToolbar(){
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    public void initializeButtons(){
        resetButton = (Button) findViewById(R.id.button6);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetData();
            }
        });

        pauseButton = (Button) findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                TimeBuff += MillisecondTime;
                handler.removeCallbacks(runnable);
                resetButton.setEnabled(true);
                wasClicked = false;

                // Set firmness button text to firmness rating or no free fall detected here

                toastIt("RECORDING PAUSED");
                toastIt("Size of list: " + String.valueOf(accData.size()));
            }
        });

        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                StartTime = SystemClock.uptimeMillis();
                handler.postDelayed(runnable, 0);
                resetButton.setEnabled(false);

                wasClicked = true;
                begin = true;
                freeFallDetected = false;
                impactDetected = false;

                accData.clear();
                forceData.clear();

                toastIt("RECORDING STARTED");
            }
        });

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveOnClick(view);
                checkPermissions();
            }
        });
        fab.setImageResource(R.drawable.ic_baseline_save_alt_24px);

        measureFirmnessButton = (Button) findViewById(R.id.circle_button);
        measureFirmnessButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                getFirmnessRating();
            }
        });
    }

    public void initializeSensors(){
        // Instantiate sensorManager
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Instantiate accelerometer and gyroscope
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        // Register the sensorManager to listen for accelerometer and gyroscope data
        sensorManager.registerListener(MeasureFirmness.this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(MeasureFirmness.this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
    }
}

