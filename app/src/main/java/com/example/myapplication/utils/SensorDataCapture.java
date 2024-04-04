package com.example.myapplication.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class SensorDataCapture implements SensorEventListener {

    private static final String TAG = "SensorDataCapture";
    private static final int BATCH_SIZE = 1;
    private static final int DATA_POINTS = 90;
    private static final int AXES = 3;
    private static final int CHANNELS = 1;

    private float[][][][] inputData = new float[BATCH_SIZE][DATA_POINTS][AXES][CHANNELS];

    private SensorManager sensorManager;
    private Sensor accelerometerSensor;

    public SensorDataCapture(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    public void startCapturing() {
        if (accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG, "Started capturing sensor data.");
        } else {
            Log.e(TAG, "Accelerometer sensor not available.");
        }
    }

    public void stopCapturing() {
        sensorManager.unregisterListener(this);
        Log.d(TAG, "Stopped capturing sensor data.");
    }

    public float[][][][] getInputData() {
        return inputData;
    }
    public static float[][][][] generateMockInputData() {
        float[][][][] mockInputData = new float[BATCH_SIZE][DATA_POINTS][AXES][CHANNELS];
        // Generate mock data
        for (int i = 0; i < BATCH_SIZE; i++) {
            for (int j = 0; j < DATA_POINTS; j++) {
                for (int k = 0; k < AXES; k++) {
                    for (int l = 0; l < CHANNELS; l++) {
                        // Generate random data between -1 and 1
                        mockInputData[i][j][k][l] = (float) Math.random() * 2 - 1;
                    }
                }
            }
        }
        return mockInputData;
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            for (int i = 0; i < DATA_POINTS; i++) {
                for (int j = 0; j < AXES; j++) {
                    inputData[0][i][j][0] = sensorEvent.values[j];
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }
}