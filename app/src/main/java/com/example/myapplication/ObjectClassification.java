package com.example.myapplication;

import static com.example.myapplication.utils.Constant.REQUEST_CODE_PERMISSIONS;
import static com.example.myapplication.utils.Constant.REQUIRED_PERMISSIONS;

import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.myapplication.utils.DetectionListener;
import com.example.myapplication.utils.ObjectDetectorHelper;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ObjectClassification extends AppCompatActivity implements DetectionListener {
    private static final String TAG = ActivityRecognition.class.getName();
    private ObjectDetectorHelper detectorHelper;

    private MappedByteBuffer loadModelFile(Activity activity, String model) throws IOException {
        AssetManager am = activity.getAssets();

        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(model);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();

        long declaredLength = fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    private void initializeModel() {
//        TfLiteInitializationOptions options = TfLiteInitializationOptions.builder().setEnableGpuDelegateSupport(true).build();
//        TfLiteVision.initialize(ObjectClassification.this, options).addOnSuccessListener(new OnSuccessListener<Void>() {
//            @Override
//            public void onSuccess(Void unused) {
//                Log.d(TAG, "Success");
//                String modelName = "2.tflite";
//                MappedByteBuffer tfliteModel;
//                try {
//                    tfliteModel = loadModelFile(ObjectClassification.this, modelName);
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//
//                Interpreter segmentationDNN;
//                Interpreter.Options options = new Interpreter.Options();
//                segmentationDNN = new Interpreter(tfliteModel, options);
//
//                int numInputs = segmentationDNN.getInputTensorCount();
//                float[][][][] dnnInput;
//                Map<Integer, Object> dnnOutputMap = new HashMap<>();
//                Vector<float[][][][]> separatedOutputs = new Vector<>();
//
//                for (int i=0; i<numInputs; i++) {
//                    Tensor inputTensor = segmentationDNN.getInputTensor(i);
//                    Log.d(TAG, Arrays.toString(inputTensor.shape()));
//                    int[] shape = inputTensor.shape();
//                    dnnInput = new float[shape[0]][shape[1]][shape[2]][shape[3]];
//                }
//
//                int numOutputs = segmentationDNN.getOutputTensorCount();
//                for (int i=0; i<numOutputs; i++) {
//                    Tensor inputTensor = segmentationDNN.getOutputTensor(i);
//                    int[] shape = inputTensor.shape();
//                    float[][][][] output = new float[shape[0]][shape[1]][shape[2]][shape[3]];
//                    dnnOutputMap.put(i, output);
//                    separatedOutputs.add(output);
//                }
//
////                ObjectDetector.ObjectDetectorOptions.Builder optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
////                        .setScoreThreshold(10)
////                        .setMaxResults(2);
////                try {
////                    mObjectDetector = ObjectDetector.createFromFileAndOptions(ActivityRecognition.this, modelName, optionsBuilder.build());
////                }catch (Exception e){
////                    Log.d(TAG,e.getMessage());
////                }
//
//            }
//        }).addOnFailureListener(new OnFailureListener() {
//            @Override
//            public void onFailure(@NonNull Exception e) {
//
//                Log.d(TAG, e.getMessage());
//            }
//        });
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
            if (grantResults.length <= 0) Log.i(TAG, "User interaction was cancelled");
            else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera();
            else Log.i(TAG, "Permission Denied");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!checkPermissions()) requestPermission();
        else startCamera();
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
                MediaScannerConnection.scanFile(ObjectClassification.this,
                        new String[]{outputUri.getPath()}, null,
                        new MediaScannerConnection.OnScanCompletedListener() {
                            @Override
                            public void onScanCompleted(String path, Uri uri) {
                                Toast toast = Toast.makeText(ObjectClassification.this, "Image Saved.", Toast.LENGTH_LONG);
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

//    private void imageAnalysis(Executor mCameraExecutor) {
//        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
//                .setTargetResolution(new Size(1280, 720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
//
//        imageAnalysis.setAnalyzer(mCameraExecutor, new ImageAnalysis.Analyzer() {
//            @Override
//            public void analyze(@NonNull ImageProxy image) {
//                int rotationDegrees = image.getImageInfo().getRotationDegrees();
//            }
//        });
//    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        Executor mCameraExecutor = Executors.newSingleThreadExecutor();

        ///////////////////////////////////// Preview Use Case ////////////////////////////////////
        //build preview to use case
        Preview preview = new Preview.Builder().build();

        //get preview output surface
        PreviewView mPreviewView = (PreviewView) findViewById(R.id.preview);
        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        cameraProvider.unbindAll();

        //bind use cases to camera
        ImageCapture imageCapture = new ImageCapture.Builder()
                .setTargetRotation(Objects.requireNonNull(this.getDisplay()).getRotation()).build();
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);

        ///////////////////////////////////////////////////////////////////////////////////////////


        ///////////////////////////////// Image Capture Use Case /////////////////////////////////

        Button capture = (Button) findViewById(R.id.capture);
        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto(imageCapture, mCameraExecutor);
                detectorHelper = new ObjectDetectorHelper();
                detectorHelper.setListener(ObjectClassification.this);
                detectorHelper.imageAnalysis(mCameraExecutor);
            }
        });

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

        ImageView close = (ImageView)findViewById(R.id.closePreview);
        initializeModel();
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ObjectClassification.this.finish();
            }
        });


        TextView result = (TextView) findViewById(R.id.result);
        ImageView imageResult = (ImageView) findViewById(R.id.imageResult);
        TextView confident = (TextView) findViewById(R.id.confident);

    }

    @Override
    public void onDetectionResult(String result) {
        // Handle detection result here
        System.out.println("Received detection result: " + result);
        // Update UI or perform other actions based on detection result
        Toast toast = Toast.makeText(ObjectClassification.this, result, Toast.LENGTH_LONG);
        toast.show();
    }
}