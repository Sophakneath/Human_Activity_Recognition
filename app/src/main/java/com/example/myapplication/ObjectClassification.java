package com.example.myapplication;

import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
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
//    private ObjectDetectorHelper detectorHelper;
    Interpreter interpreterApi;
    private static final String MODEL_PATH = "classification_model.tflite";
    ImageCapture imageCapture;
    Executor mCameraExecutor = Executors.newSingleThreadExecutor();
    TextView result, confident;
    ImageView close, imageResult;
    Button capture;
    private static final int INPUT_SIZE = 224;
    private static final int NUM_CLASSES = 1000;

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
        ContentValues contentValues = new ContentValues();
        long currentTime = System.currentTimeMillis();

        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()));
        contentValues.put(MediaStore.Images.Media.DATE_TAKEN, currentTime);
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build();

        imageCapture.takePicture(outputFileOptions, mCameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Uri outputUri = outputFileResults.getSavedUri();
//                runOnUiThread(() -> imageResult.setImageBitmap(outputUri));
                imageAnalysis();
//                Uri outputUri = outputFileResults.getSavedUri();
//                MediaScannerConnection.scanFile(ObjectClassification.this,
//                        new String[]{outputUri.getPath()}, null,
//                        new MediaScannerConnection.OnScanCompletedListener() {
//                            @Override
//                            public void onScanCompleted(String path, Uri uri) {
//                                Toast toast = Toast.makeText(ObjectClassification.this, "Image Saved.", Toast.LENGTH_LONG);
//                                toast.show();
//                                result.setImageURI(file.toURI());
//                            }
//                        });
//                Log.d(TAG, "Camera Saved");
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("CameraX", "Error capturing image", exception);
            }
        }) ;
    }

    private ByteBuffer preprocessImage(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        inputBuffer.rewind();
        resizedBitmap.copyPixelsToBuffer(inputBuffer);
        return inputBuffer;
    }

    private Bitmap imageToBitmap(ImageProxy image) {
        // Convert ImageProxy to Bitmap
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private void imageAnalysis() {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

        imageAnalysis.setAnalyzer(mCameraExecutor, image -> {
            Bitmap bitmap = imageToBitmap(image);
            runOnUiThread(() -> imageResult.setImageBitmap(bitmap));
//            int rotationDegrees = image.getImageInfo().getRotationDegrees();
            float[] output = runInference(bitmap);
            if (output != null) {
                Recognition topPrediction = processOutput(output, loadLabels());
                if (topPrediction != null) {
                    String predictedClass = topPrediction.getLabel();
                    float confidence = topPrediction.getConfidence();
                    Log.d("Prediction", "Predicted Class: " + predictedClass + ", Confidence: " + confidence);
                    updateUI(predictedClass, confidence);
                } else {
                    Log.d("Prediction", "No class found");
                }
            }
        });
    }

    private void updateUI(String predictedClass, float confidence) {
        result.setText(predictedClass);
        confident.setText(String.valueOf(confidence));
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

    private float[] runInference(Bitmap bitmap) {
        if (interpreterApi == null) {
            Log.e("TFLite", "Interpreter is not initialized.");
            return null;
        }
        ByteBuffer inputBuffer = preprocessImage(bitmap);
        float[][] outputScores = new float[1][NUM_CLASSES];
        interpreterApi.run(inputBuffer, outputScores);
        return outputScores[0];
    }

    // Load labels from text file
    private List<String> loadLabels() {
        List<String> labels = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("activity_labels.txt")));
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
    private Recognition processOutput(float[] outputScores, List<String> labels) {
        int topClassIndex = -1;
        float topScore = -Float.MAX_VALUE;

        for (int i = 0; i < outputScores.length; i++) {
            if (outputScores[i] > topScore) {
                topScore = outputScores[i];
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

        //bind use cases to camera
        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(Objects.requireNonNull(this.getDisplay()).getRotation()).build();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

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
//                detectorHelper = new ObjectDetectorHelper();
//                detectorHelper.setListener(ObjectClassification.this);
//                detectorHelper.imageAnalysis(mCameraExecutor);
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!checkPermissions()) requestPermission();
        else startCamera();
    }

//    @Override
//    public void onDetectionResult(String result) {
//        // Handle detection result here
//        System.out.println("Received detection result: " + result);
//        // Update UI or perform other actions based on detection result
//        Toast toast = Toast.makeText(ObjectClassification.this, result, Toast.LENGTH_LONG);
//        toast.show();
//    }
}