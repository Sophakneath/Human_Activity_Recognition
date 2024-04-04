package com.example.myapplication;

import android.content.Context;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.utils.SensorDataCapture;
import com.github.mikephil.charting.charts.LineChart;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityRecognition extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = ActivityRecognition.class.getName();
    SensorManager sensorManager;
    List<Sensor> sensorList;
    Sensor accSensor;
    SensorEventListener sensorEventListener;
    SensorDataCapture sensorDataCapture;
    private String[] activities = {"Downstairs", "Jogging", "Sitting", "Standing", "Upstairs", "Walking"};

    ImageView imageResult;
    TextView confident, resultView;
    LineChart lineChart;

    Interpreter interpreterApi;
    private Sensor accelerometerSensor;

    private static final int BATCH_SIZE = 1;
    private static final int DATA_POINTS = 90;
    private static final int AXES = 3;
    private static final int CHANNELS = 1;
    private static List<Float> ax;
    private static List<Float> ay;
    private static List<Float> az;

    private List<List<Float>> xyz;
    int windowSize = 45;
    private float[][][][] inputData = new float[BATCH_SIZE][DATA_POINTS][AXES][CHANNELS];

    private int counter = 0;

    private static List<Float> ma;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_act_recognition);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sensorDataCapture(this);

        // Load the TFLite model
        AssetManager assetManager = this.getAssets();
        try{
            Interpreter.Options options = new Interpreter.Options();
            options.setUseNNAPI(true);
            Interpreter interpreter = new Interpreter(loadModelFile(assetManager), options);
            // Finish interpreter initialization
            this.interpreterApi = interpreter;
            Log.d("TAG", "Initialized TFLite interpreter.");
        }catch (Exception e){
            Log.d("","xee:"+e.getMessage());
        }

        ax = new ArrayList<>(); ay = new ArrayList<>(); az = new ArrayList<>();
        ImageView close = (ImageView)findViewById(R.id.closePreview);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorList = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
        accSensor = sensorList.get(0);

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityRecognition.this.finish();
            }
        });

        Button start = (Button) findViewById(R.id.start);
        Button end = (Button) findViewById(R.id.end);
        imageResult = (ImageView) findViewById(R.id.imageResult);
        confident = (TextView) findViewById(R.id.confident);
        lineChart = (LineChart) findViewById(R.id.graph);
        resultView = (TextView) findViewById(R.id.result);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCapturing();
            }
        });

        end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopCapturing();
            }
        });
    }

    // File path to the TFLite model
    private static final String MODEL_PATH = "activity_recognition_model.tflite";

    // Number of classes for human activity recognition
    private static final int NUM_CLASSES = 6; // Example: Walking, Running, Sitting, Standing, etc.
    private static final int NUM_DATA_POINTS = 90;
    private static final int NUM_AXES = 3;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Method to load the TFLite model file
    private ByteBuffer loadModelFile(AssetManager assetManager) throws IOException {
        FileInputStream inputStream = null;
        FileChannel fileChannel = null;
        ByteBuffer buffer = null;
        try {
            inputStream = new FileInputStream(assetManager.openFd(MODEL_PATH).getFileDescriptor());
            fileChannel = inputStream.getChannel();
            long startOffset = assetManager.openFd(MODEL_PATH).getStartOffset();
            long declaredLength = assetManager.openFd(MODEL_PATH).getDeclaredLength();
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (fileChannel != null) {
                fileChannel.close();
            }
        }
        return buffer;
    }

    private int inputImageWidth;
    private int inputImageHeight;
    private int modelInputSize;
    private static final int FLOAT_TYPE_SIZE = 4;
    private static final int PIXEL_SIZE = 3;

    float[][] predict(){
        // Read input shape from model file
        int[] inputShape = interpreterApi.getInputTensor(0).shape();
        inputImageWidth = inputShape[1];
        inputImageHeight = inputShape[2];
        modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * PIXEL_SIZE;
        long startTime = System.nanoTime();
        float[][] result = new float[1][6];
        if (interpreterApi != null) {
            interpreterApi.run(inputData, result);
            // Do something with the classification result

            for (float i : result[0]) {
                Log.d("TAG", "Classification Result: " + i);
            }

            int index = findPredictedActivity(result[0]);
            Log.d("TAG", String.valueOf(result[0]));
            resultView.setText(activities[index]);
        }
        long elapsedTime = (System.nanoTime() - startTime) / 1000000;
        Log.d("TAG", "Inference time = " + elapsedTime + "ms");
        return result;
    }

//     Method to find the predicted activity from output scores
    private int findPredictedActivity(float[] outputScores) {
        // Find index of highest score
        int maxIndex = 0;
        for (int i = 1; i < outputScores.length; i++) {
            if (outputScores[i] > outputScores[maxIndex]) {
                maxIndex = i;
            }
        }
        // Return index of predicted activity
        return maxIndex;
    }

    private static float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            ax.add(sensorEvent.values[0]);
            ay.add(sensorEvent.values[1]);
            az.add(sensorEvent.values[2]);

            if (ax.size() == 270 && ay.size() == 270 && az.size() == 270) {
                ax = ax.subList(ax.size() - 46, ax.size() - 1);
                ay = ay.subList(ay.size() - 46, ay.size() - 1);
                az = az.subList(az.size() - 46, az.size() - 1);
                Log.d("TAG", "ax: " + ax.size());
                counter = 45;
            }
            counter++;
        }
        if (ax.size() == DATA_POINTS && ay.size() == DATA_POINTS && az.size() == DATA_POINTS) {
            Log.d("TAG", "Counter first: " + counter);
            counter = 0;
            for (int i = 0; i < DATA_POINTS; i++) {
                inputData[0][i][0][0] = ax.get(i);
                inputData[0][i][1][0] = ay.get(i);
                inputData[0][i][2][0] = az.get(i);
            }
            predict();
        }
        else {
            if (counter == DATA_POINTS) {
                Log.d("TAG", "Counter Second: " + counter);
                Log.d("TAG", "List size: " + ax.size());
                counter = 0;
                for (int i = ax.size() - DATA_POINTS - windowSize; i < ax.size() - windowSize; i++) {
                    inputData[0][i - windowSize][0][0] = ax.get(i);
                    inputData[0][i - windowSize][1][0] = ay.get(i);
                    inputData[0][i - windowSize][2][0] = az.get(i);
                }
                predict();
            }
        }
    }

    public void startCapturing() {
        if (accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);
            Log.d("TAG", "Started capturing sensor data.");
        } else {
            Log.e("TAG", "Accelerometer sensor not available.");
        }
    }

    public void stopCapturing() {
        sensorManager.unregisterListener(this);
        Log.d("TAG", "Stopped capturing sensor data.");
    }

    public void sensorDataCapture(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    private void slidingWindow() {
        int windowSize = 45;
    }
}