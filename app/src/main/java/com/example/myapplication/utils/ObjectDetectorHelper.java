package com.example.myapplication.utils;

import android.graphics.Bitmap;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import java.util.concurrent.Executor;

public class ObjectDetectorHelper {
    private DetectionListener listener;

    // Set the listener
    public void setListener(DetectionListener listener) {
        this.listener = listener;
    }

    public void imageAnalysis(Executor mCameraExecutor) {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

        final Bitmap[] cameraFrameBuffer = {null};
        final int dnnInputSize = 257;

        imageAnalysis.setAnalyzer(mCameraExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                int rotationDegrees = image.getImageInfo().getRotationDegrees();

                if (cameraFrameBuffer[0] == null) {
                    cameraFrameBuffer[0] = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                }

                cameraFrameBuffer[0].copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());




                //coding here
                // Preprocess the image and convert it into a TensorImage for detection
//                ImageProcessor imageProcessor = new ImageProcessor.Builder()
//                        .add(new Rot90Op(rotationDegrees / 90))
//                        .build();

                // Preprocess the image and convert it into a TensorImage for detection.
//                TensorImage tensorImage = imageProcessor.process(TensorImage.fromBitmap(image.toBitmap()));
//
//                List<Detection> results = mObjectDetector.detect(tensorImage);
//                Log.d("",listener.toString());
//                if (listener != null) {
//                    listener.onDetectionResult(String.valueOf(results.size()));
//                }
            }
        });

    }

//    private ObjectDetector initializeModel() throws IOException {
//        String modelName = "activity_recognition_model.tflite";
//        ObjectDetector.ObjectDetectorOptions.Builder optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
//                .setScoreThreshold(10)
//                .setMaxResults(2);
//
//        return ObjectDetector.createFromFileAndOptions(ActivityRecognition.this, modelName, optionsBuilder.build());
//    }
}
