package com.example.myapplication;

import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.utils.Recognition;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.example.myapplication.utils.Constant.REQUEST_CODE_PERMISSIONS;
import static com.example.myapplication.utils.Constant.REQUIRED_PERMISSIONS;

public class ObjectClassification extends AppCompatActivity {
    private static final String TAG = ActivityRecognition.class.getName();
    Interpreter interpreterApi;
    private static final String MODEL_PATH = "classification_model.tflite";
    ImageCapture imageCapture;
    ImageAnalysis imageAnalysis;
    Executor mCameraExecutor = Executors.newSingleThreadExecutor();
    TextView result, confident;
    ImageView close, imageResult;
    Button capture;

    private ByteBuffer loadModelFile(AssetManager assetManager) throws IOException {
        FileInputStream inputStream = null;
        FileChannel fileChannel = null;
        ByteBuffer buffer;
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

    private boolean checkPermissions() {
        for(String permission: REQUIRED_PERMISSIONS){
            int permissionState = ActivityCompat.checkSelfPermission(this, permission);
            if(permissionState != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length == 0) Log.i(TAG, "User interaction was cancelled");
            else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera();
            else Log.i(TAG, "Permission Denied");
        }
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bindUseCases(cameraProvider);
                }
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void CaptureImage(ImageCapture imageCapture) {
        imageAnalysis.setAnalyzer(mCameraExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                Bitmap bitmap = image.toBitmap();

//              Preprocess the input Bitmap to match the input requirements of the model
                ByteBuffer inputBuffer = preprocessImage(bitmap);

                List<String> classLabels = loadLabels();
                int NUM_CLASSES = classLabels.size();

                // Run inference
                if (interpreterApi == null) {
                    Log.e("TFLite", "Interpreter is not initialized.");
                }

                float[][] outputScores = new float[1][NUM_CLASSES];
                interpreterApi.run(inputBuffer, outputScores);

                image.close();

                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap rotation = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                runOnUiThread(() -> {
                    imageResult.setImageBitmap(rotation);
                    imageResult.setScaleType(ImageView.ScaleType.CENTER_CROP);
                });

                // Post-process the output
                Recognition topPrediction = processOutput(outputScores, loadLabels());
                if (topPrediction != null) {
                    String predictedClass = topPrediction.getLabel();
                    float confidence = topPrediction.getConfidence();
                    Log.d("Prediction", "Predicted Class: " + predictedClass + ", Confidence: " + confidence);
                    updateUI(predictedClass, formatFloat(confidence));
                } else {
                    Log.d("Prediction", "No class found");
                }
            }
        });
    }

    private ByteBuffer preprocessImage(Bitmap bitmap) {
        float IMAGE_MEAN = 0.1f;
        float IMAGE_STD = 1.0f;
        int[] inputShape = interpreterApi.getInputTensor(0).shape();
        int inputWidth = inputShape[1];
        int inputHeight = inputShape[2];
        int channels = inputShape[3];
        int bytesPerChannel = Float.SIZE / Byte.SIZE;

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true);
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * channels * bytesPerChannel);
        inputBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[inputWidth * inputHeight];
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());
        int pixel = 0;

        for (int i = 0; i < inputWidth; ++i) {
            for (int j = 0; j < inputHeight; ++j) {
                int val = intValues[pixel++];
                inputBuffer.putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                inputBuffer.putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                inputBuffer.putFloat(((val & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }
        return inputBuffer;
    }

    private String formatFloat(float d) {
        DecimalFormat df = new DecimalFormat("#.###");
        return df.format(d);
    }

    private void updateUI(String predictedClass, String confidence) {
        result.setText(predictedClass);
        confident.setText(confidence);
    }

    private void initInterpreter() {
        // Load the TFLite model
        AssetManager assetManager = this.getAssets();
        try{
            Interpreter.Options options = new Interpreter.Options();
            options.setUseNNAPI(true);
            // Finish interpreter initialization
            this.interpreterApi = new Interpreter(loadModelFile(assetManager), options);
            Log.d("TAG", "Initialized TFLite interpreter.");
        }catch (Exception e){
            Log.d("","xee:"+e.getMessage());
        }
    }

    // Load labels from text file
    private List<String> loadLabels() {
        List<String> labels = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("imagenet_labels.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line);
            }
            reader.close();
        } catch (IOException e) {
            Log.e("TFLite", "Error reading labels", e);
        }
        return labels;
    }

    // Process output scores: find top k classes
    private Recognition processOutput(float[][] outputScores, List<String> labels) {
        int topClassIndex = -1;
        float topScore = -Float.MAX_VALUE;

        for (int i = 0; i < outputScores[0].length; i++) {
            if (outputScores[0][i] > topScore) {
                topScore = outputScores[0][i];
                topClassIndex = i;
            }
        }

        if (topClassIndex != -1) {
            String topClassLabel = labels.get(topClassIndex);
            return new Recognition(String.valueOf(topClassIndex), topClassLabel, topScore);
        } else {
            return null; // No class found
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

        ///////////////////////////////////// Preview Use Case ////////////////////////////////////
        //build preview to use case
        Preview preview = new Preview.Builder().build();

        //get preview output surface
        PreviewView mPreviewView = findViewById(R.id.preview);
        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        cameraProvider.unbindAll();

        imageAnalysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

        ///////////////////////////////////////////////////////////////////////////////////////////
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_obj_classification);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        close = findViewById(R.id.closePreview);
        result = findViewById(R.id.result);
        imageResult = findViewById(R.id.imageResult);
        confident = findViewById(R.id.confident);
        capture = findViewById(R.id.capture);

        initInterpreter();
        close.setOnClickListener(view -> ObjectClassification.this.finish());
        capture.setOnClickListener(v -> {
            CaptureImage(imageCapture);
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!checkPermissions()) requestPermission();
        else startCamera();
    }
}