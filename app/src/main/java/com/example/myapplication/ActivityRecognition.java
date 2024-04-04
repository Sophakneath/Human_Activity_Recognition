package com.example.myapplication;

import static com.example.myapplication.utils.Constant.REQUEST_CODE_CHILD_ACTIVITY;
import static com.example.myapplication.utils.Constant.REQUEST_CODE_PERMISSIONS;
import static com.example.myapplication.utils.Constant.REQUIRED_PERMISSIONS;

import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.myapplication.utils.DetectionListener;
import com.example.myapplication.utils.ObjectDetectorHelper;
import com.example.myapplication.utils.SensorDataCapture;
import com.github.mikephil.charting.charts.LineChart;
import com.google.common.util.concurrent.ListenableFuture;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

public class ActivityRecognition extends AppCompatActivity implements DetectionListener {
    private ObjectDetectorHelper detectorHelper;
    private static final String TAG = ActivityRecognition.class.getName();
    LinearLayout mainView;
    ConstraintLayout secondView;
    Interpreter interpreterApi;
    private boolean isInitialized;
    private int inputImageWidth;
    private int inputImageHeight;
    private int modelInputSize;
    private static final String MODEL_FILE = "activity_recognition_model.tflite";
    private static final int FLOAT_TYPE_SIZE = 4;
    private static final int PIXEL_SIZE = 3;
//    private ObjectDetector initializeModel() throws IOException {
////        TfLiteInitializationOptions options = TfLiteInitializationOptions.builder().setEnableGpuDelegateSupport(true).build();
////        TfLiteVision.initialize(ActivityRecognition.this, options).addOnSuccessListener(new OnSuccessListener<Void>() {
////            @Override
////            public void onSuccess(Void unused) {
////
////            }
////        }).addOnFailureListener(new OnFailureListener() {
////            @Override
////            public void onFailure(@NonNull Exception e) {
////
////            }
////        });
//
//        String modelName = "activity_recognition_model.tflite";
//        ObjectDetector.ObjectDetectorOptions.Builder optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
//                        .setScoreThreshold(10)
//                        .setMaxResults(2);
//
//        ObjectDetector objectDetector= ObjectDetector.createFromFileAndOptions(ActivityRecognition.this, modelName, optionsBuilder.build());
//        return objectDetector;
//    }

    public void initializeInterpreter() {
        // Load the TF Lite model
        AssetManager assetManager = this.getAssets();
        ByteBuffer model = null;
       try{
            model = loadModelFile(assetManager);
       }catch (Exception e){
           Log.d("","xee:"+e.getMessage());
       }
       if (model == null)  Log.d("","xee:"+"LOAD FAILED");

        // Initialize TF Lite Interpreter with NNAPI enabled
        Interpreter.Options options = new Interpreter.Options();
        options.setUseNNAPI(true);
        Interpreter interpreter = new Interpreter(model,options);


        // Finish interpreter initialization
        this.interpreterApi = interpreter;
        isInitialized = true;
        Log.d(TAG, "Initialized TFLite interpreter.");

        // TEST
        FutureTask<float[][]> classificationTask = classifyAsync();
        try {
            float[][] result = classificationTask.get();
            // Do something with the classification result

            for (float i : result[0]) {
                System.out.println("Classification Result: " + i);
            }
        } catch (InterruptedException | ExecutionException e) {
            // Handle any exceptions that may occur
            e.printStackTrace();
        }
    }
    float[][] predict(){
        // Read input shape from model file
        int[] inputShape = interpreterApi.getInputTensor(0).shape();
        inputImageWidth = inputShape[1];
        inputImageHeight = inputShape[2];
        modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * PIXEL_SIZE;
        long startTime = System.nanoTime();
        float[][] result = new float[1][6];
        if (interpreterApi != null) {

            interpreterApi.run(SensorDataCapture.generateMockInputData(), result);
        }
        long elapsedTime = (System.nanoTime() - startTime) / 1000000;
        Log.d(TAG, "Inference time = " + elapsedTime + "ms");
        return result;
    }
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public FutureTask<float[][]> classifyAsync() {
        FutureTask<float[][]> task = new FutureTask<>(() -> predict());
        executorService.execute(task);
        return task;
    }

    private ByteBuffer loadModelFile(AssetManager assetManager) throws IOException {
        FileInputStream inputStream = null;
        FileChannel fileChannel = null;
        ByteBuffer buffer = null;
        try {
            inputStream = new FileInputStream(assetManager.openFd(MODEL_FILE).getFileDescriptor());
            fileChannel = inputStream.getChannel();
            long startOffset = assetManager.openFd(MODEL_FILE).getStartOffset();
            long declaredLength = assetManager.openFd(MODEL_FILE).getDeclaredLength();
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

    @Override
    public void onDetectionResult(String result) {
        // Handle detection result here
        System.out.println("Received detection result: " + result);
        // Update UI or perform other actions based on detection result
        Toast toast = Toast.makeText(ActivityRecognition.this, result, Toast.LENGTH_LONG);
        toast.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeInterpreter();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_act_recognition);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageView close = (ImageView)findViewById(R.id.closePreview);
        mainView = findViewById(R.id.mainView);
        secondView = findViewById(R.id.secondView);

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityRecognition.this.finish();
            }
        });

        Button start = (Button) findViewById(R.id.start);
        ImageView imageResult = (ImageView) findViewById(R.id.imageResult);
        TextView confident = (TextView) findViewById(R.id.confident);
        LineChart lineChart = (LineChart) findViewById(R.id.graph);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Intent myIntent = new Intent(ActivityRecognition.this, CameraPreviewActivity.class);
//                ActivityRecognition.this.startActivityForResult(myIntent, REQUEST_CODE_CHILD_ACTIVITY);
                mainView.setVisibility(View.GONE);
                secondView.setVisibility(View.VISIBLE);
                if (!checkPermissions()) requestPermission();
                else startCamera();
            }
        });
    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
////        if (requestCode == REQUEST_CODE_CHILD_ACTIVITY) {
////            if (resultCode == RESULT_OK) {
////                TextView txtViewResult = (TextView) findViewById(R.id.result);
////                String result = data.getStringExtra("result");
////                txtViewResult.setText(result);
////            }
////        }
//        detectorHelper = new ObjectDetectorHelper();
//        detectorHelper.setListener(this);
//        Executor mCameraExecutor = Executors.newSingleThreadExecutor();
//        try {
//            detectorHelper.imageAnalysis(mCameraExecutor, initializeModel());
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

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
            if (grantResults.length <= 0) Log.i(TAG, "User interaction was cancelled");
            else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera();
            else Log.i(TAG, "Permission Denied");
        }
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        bindUseCases(cameraProvider);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto(ImageCapture imageCapture, Executor mCameraExecutor) {
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
                MediaScannerConnection.scanFile(ActivityRecognition.this,
                        new String[]{outputUri.getPath()}, null,
                        new MediaScannerConnection.OnScanCompletedListener() {
                            @Override
                            public void onScanCompleted(String path, Uri uri) {
                                Toast toast = Toast.makeText(ActivityRecognition.this, "Image Saved.", Toast.LENGTH_LONG);
                                toast.show();
                            }
                        });
                Log.d(TAG, "Camera Saved");
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {

            }
        }) ;
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        Executor mCameraExecutor = Executors.newSingleThreadExecutor();

        ///////////////////////////////////// Preview Use Case ////////////////////////////////////
        //build preview to use case
        Preview preview = new Preview.Builder().build();

        //get preview output surface
        PreviewView mPreviewView = (PreviewView) findViewById(R.id.previewView);
        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        cameraProvider.unbindAll();

        //bind use cases to camera
        ImageCapture imageCapture = new ImageCapture.Builder()
                .setTargetRotation(Objects.requireNonNull(this.getDisplay()).getRotation()).build();
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);

        ///////////////////////////////////////////////////////////////////////////////////////////


        ///////////////////////////////// Image Capture Use Case /////////////////////////////////

        Button capture = findViewById(R.id.capture);
        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePhoto(imageCapture, mCameraExecutor);
                mainView.setVisibility(View.VISIBLE);
                secondView.setVisibility(View.GONE);

                detectorHelper = new ObjectDetectorHelper();
                detectorHelper.setListener(ActivityRecognition.this);
//                try {
//                    detectorHelper.imageAnalysis(mCameraExecutor, initializeModel());
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
            }
        });

        ///////////////////////////////////////////////////////////////////////////////////////////
    }
}