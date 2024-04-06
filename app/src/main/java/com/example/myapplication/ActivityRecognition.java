package com.example.myapplication;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
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

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityRecognition extends AppCompatActivity implements SensorEventListener {
    SensorManager sensorManager;
    List<Sensor> sensorList;
    Sensor accSensor;
    private final String[] activities = {"Downstairs", "Jogging", "Sitting", "Standing", "Upstairs", "Walking"};

    ImageView imageResult, close;
    TextView confident, resultView;
    LineChart lineChart;
    Button start, end;

    Interpreter interpreterApi;
    private Sensor accelerometerSensor;

    private static final int BATCH_SIZE = 1;
    private static final int DATA_POINTS = 90;
    private static final int AXES = 3;
    private static final int CHANNELS = 1;
    private static List<Float> ax;
    private static List<Float> ay;
    private static List<Float> az;

    List<Entry> entriesX, entriesY, entriesZ;
    LineDataSet dataSetX, dataSetY, dataSetZ;
    LineData lineData;
    private float[][][][] inputData = new float[BATCH_SIZE][DATA_POINTS][AXES][CHANNELS];
    private static final String MODEL_PATH = "activity_recognition_model.tflite";

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

        // Load the TFLite model
        initInterpreter();

        sensorDataCapture(this);

        ax = new ArrayList<>();
        ay = new ArrayList<>();
        az = new ArrayList<>();
        entriesX = new ArrayList<>();
        entriesY = new ArrayList<>();
        entriesZ = new ArrayList<>();
        dataSetX = new LineDataSet(entriesX, "Acceleration X");
        dataSetY = new LineDataSet(entriesY, "Acceleration Y");
        dataSetZ = new LineDataSet(entriesZ, "Acceleration Z");

        close = findViewById(R.id.closePreview);
        start = findViewById(R.id.start);
        end = findViewById(R.id.end);
        imageResult = findViewById(R.id.imageResult);
        confident = findViewById(R.id.confident);
        lineChart = findViewById(R.id.graph);
        resultView = findViewById(R.id.result);

        lineData = new LineData();
        lineChart.setData(lineData);

        lineChart.getDescription().setEnabled(false);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(true);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setEnabled(true);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMaximum(16f);
        leftAxis.setAxisMinimum(2f);
        leftAxis.setDrawGridLines(true);

        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                // Customize the Y axis value format here
                return String.format("%.0f", value); // Format value to 2 decimal places
            }
        });
        leftAxis.setTextColor(Color.BLACK);

        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setDrawGridLines(true);
        rightAxis.setAxisMaximum(16f);
        rightAxis.setAxisMinimum(2f);
        rightAxis.setEnabled(true);

        rightAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                // Customize the Y axis value format here
                return String.format("%.0f", value); // Format value to 2 decimal places
            }
        });
        rightAxis.setTextColor(Color.BLACK);

        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getXAxis().setDrawGridLines(true);
        lineChart.setDrawBorders(true);

        feedMultiple();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorList = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
        accSensor = sensorList.get(0);

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityRecognition.this.finish();
            }
        });

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
                if (thread != null) {
                    thread.interrupt();
                    lineChart.clearValues();
                    resultView.setText(R.string.no_result_available);
                    confident.setText(R.string.confident_0);
                    imageResult.setImageResource(R.drawable.no_pictures);
                }
            }
        });
    }

    private void addEntry(SensorEvent event) {

        LineData data = lineChart.getData();

        if (data != null) {

            ILineDataSet setX = data.getDataSetByIndex(0);
            ILineDataSet setY = data.getDataSetByIndex(1);
            ILineDataSet setZ = data.getDataSetByIndex(2);

            if (setX == null) {
                setX = createSet(Color.RED, "Accelerometer X");
                data.addDataSet(setX);
                setY = createSet(Color.BLUE, "Accelerometer Y");
                data.addDataSet(setY);
                setZ = createSet(Color.GREEN, "Accelerometer Z");
                data.addDataSet(setZ);
            }

            data.addEntry(new Entry(setX.getEntryCount(), event.values[0] + 5), 0);
            data.addEntry(new Entry(setY.getEntryCount(), event.values[1] + 5), 1);
            data.addEntry(new Entry(setZ.getEntryCount(), event.values[2] + 5), 2);
            data.notifyDataChanged();

            // let the chart know it's data has changed
            lineChart.notifyDataSetChanged();

            // limit the number of visible entries
            lineChart.setVisibleXRangeMaximum(10);
            lineChart.setVisibleYRange(2,16, YAxis.AxisDependency.LEFT);

            // move to the latest entry
            lineChart.moveViewToX(data.getEntryCount());
        }
    }

    private LineDataSet createSet(int color, String label) {
        LineDataSet set = new LineDataSet(null, label);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(color);
        set.setLineWidth(3f);
        set.setHighlightEnabled(false);
        set.setDrawValues(true);
        set.setDrawCircles(false);
        set.setCubicIntensity(0.2f);
        return set;
    }

    private Thread thread;
    private boolean plotData = true;

    private void feedMultiple() {

        if (thread != null){
            thread.interrupt();
        }

        thread = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true){
                    plotData = true;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        });

        thread.start();
    }

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

    private void initInterpreter() {
        AssetManager assetManager = this.getAssets();
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setUseNNAPI(true);
            Interpreter interpreter = new Interpreter(loadModelFile(assetManager), options);
            // Finish interpreter initialization
            this.interpreterApi = interpreter;
            Log.d("TAG", "Initialized TFLite interpreter.");
        } catch (Exception e) {
            Log.d("", "xee:" + e.getMessage());
        }
    }

    float[][] predict(){
        long startTime = System.nanoTime();
        float[][] result = new float[BATCH_SIZE][activities.length];
        if (interpreterApi != null) {
            interpreterApi.run(inputData, result);
            // Do something with the classification result
            for (float i : result[0]) {
                Log.d("TAG", "Classification Result: " + i);
            }

            int index = findPredictedActivity(result[0]);
            Log.d("TAG", Arrays.toString(result[0]));
            setUI(index, result);
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

    private void setUI(int index, float[][] result) {
        int resId = 0;
        switch (index) {
            case 0: resId = R.drawable.downstair; break;
            case 1: resId = R.drawable.jogging; break;
            case 2: resId = R.drawable.sitting; break;
            case 3: resId = R.drawable.standing; break;
            case 4: resId = R.drawable.upstair; break;
            case 5: resId = R.drawable.walking; break;
        }
        resultView.setText(activities[index]);
        confident.setText(formatFloat(result[0][index]));
        imageResult.setImageResource(resId);
    }

    private String formatFloat(float d) {
        DecimalFormat df = new DecimalFormat("#.###");
        return df.format(d);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(ActivityRecognition.this);
        thread.interrupt();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            ax.add(sensorEvent.values[0]);
            ay.add(sensorEvent.values[1]);
            az.add(sensorEvent.values[2]);

            if(plotData){
                addEntry(sensorEvent);
                plotData = false;
            }

            if (ax.size() == DATA_POINTS && ay.size() == DATA_POINTS && az.size() == DATA_POINTS) {
                Log.d("TAG", "Counter first: " + ax.size());
                for (int i = 0; i < DATA_POINTS; i++) {
                    inputData[0][i][0][0] = ax.get(i);
                    inputData[0][i][1][0] = ay.get(i);
                    inputData[0][i][2][0] = az.get(i);
                }
                predict();
                ax = ax.subList(ax.size() - 46, ax.size() - 1);
                ay = ay.subList(ay.size() - 46, ay.size() - 1);
                az = az.subList(az.size() - 46, az.size() - 1);
                Log.d("TAG", "Counter Second: " + ax.size());
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
}