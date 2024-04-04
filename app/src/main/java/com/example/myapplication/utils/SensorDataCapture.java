package com.example.myapplication.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class SensorDataCapture {
    private static final String TAG = "SensorDataCapture";
    private static final int BATCH_SIZE = 1;
    private static final int DATA_POINTS = 90;
    private static final int AXES = 3;
    private static final int CHANNELS = 1;
    private static List<Float> ax;
    private static List<Float> ay;
    private static List<Float> az;

    private float[][][][] inputData = new float[BATCH_SIZE][DATA_POINTS][AXES][CHANNELS];

    private SensorManager sensorManager;
    private Sensor accelerometerSensor;

    public SensorDataCapture(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        ax = new ArrayList<>(); ay = new ArrayList<>(); az = new ArrayList<>();
        if (sensorManager != null) {
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
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
}
