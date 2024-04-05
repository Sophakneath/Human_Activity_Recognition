package com.example.myapplication;

import static com.example.myapplication.utils.Constant.REQUEST_CODE_PERMISSIONS;
import static com.example.myapplication.utils.Constant.REQUIRED_PERMISSIONS;

import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    // File path to the TFLite model
    private static final String MODEL_PATH = "1.tflite";
    private Interpreter interpreterApi;

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
    private void initializeModel() {
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

    private String predict(Bitmap bitmap){
        String[] classLabels = new String[]{"Label 1", "Label 2", "Label 3"};
        int NUM_CLASSES = 100;
        float IMAGE_MEAN = 0.1f;
        float IMAGE_STD = 1.0f;


        int[] inputShape = interpreterApi.getInputTensor(0).shape();
        int inputWidth = inputShape[1];
        int inputHeight = inputShape[2];
        int channels = inputShape[3];
        DataType inputDataType = interpreterApi.getInputTensor(0).dataType();
        int bytesPerChannel;
        switch (inputDataType) {
            case FLOAT32:
                bytesPerChannel = Float.SIZE / Byte.SIZE;
                break;
            case INT8:
                bytesPerChannel = 1;
                break;
            case UINT8:
                bytesPerChannel = 1;
                break;
            default:
                // Handle unsupported data types
                throw new IllegalArgumentException("Unsupported data type: " + inputDataType);
        }

        // Preprocess the input Bitmap to match the input requirements of the model
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

        // Run inference
        float[][] outputScores = new float[1][NUM_CLASSES];
        interpreterApi.run(inputBuffer, outputScores);

        // Post-process the output if needed
        // For example, finding the index with the highest score
        int maxIndex = 0;
        for (int i = 0; i < NUM_CLASSES; i++) {
            Log.d("Result: ",outputScores[0][i]+"");
            if (outputScores[0][i] > outputScores[0][maxIndex]) {
                maxIndex = i;
            }
        }

        // Return the prediction result
        return classLabels[maxIndex];

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
                Bitmap mockBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.downstair);
                predict(mockBitmap);
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