package com.example.myapplication.utils;

import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.example.myapplication.ActivityRecognition;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tflite.client.TfLiteInitializationOptions;

import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.task.gms.vision.TfLiteVision;
import org.tensorflow.lite.task.gms.vision.detector.Detection;
import org.tensorflow.lite.task.gms.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

public class ObjectDetectorHelper {
    private DetectionListener listener;

    // Set the listener
    public void setListener(DetectionListener listener) {
        this.listener = listener;
    }

    public void imageAnalysis(Executor mCameraExecutor, ObjectDetector mObjectDetector) {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

        imageAnalysis.setAnalyzer(mCameraExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                int rotationDegrees = image.getImageInfo().getRotationDegrees();
                //coding here
                // Preprocess the image and convert it into a TensorImage for detection
                ImageProcessor imageProcessor = new ImageProcessor.Builder()
                        .add(new Rot90Op(rotationDegrees / 90))
                        .build();

                // Preprocess the image and convert it into a TensorImage for detection.
                TensorImage tensorImage = imageProcessor.process(TensorImage.fromBitmap(image.toBitmap()));

                ObjectDetector objectDetector = null;
                objectDetector = mObjectDetector;

                List<Detection> results = objectDetector.detect(tensorImage);
                if (listener != null) {
                    listener.onDetectionResult(String.valueOf(results.size()));
                }
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
