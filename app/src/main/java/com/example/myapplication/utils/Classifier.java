package com.example.myapplication.utils;

import android.content.Context;
import android.util.Log;

//import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

public class Classifier {
    static {
        System.loadLibrary("tensorflow_inference");
    }

//    private TensorFlowInferenceInterface inferenceInterface;
    private static final String MODEL_FILE = "/Users/phakneath/Desktop/Mobile_HW1/app/src/main/assets/activity_recognition_model.tflite";
    private static final String INPUT_NODE = "LSTM_1_input";
    private static final String[] OUTPUT_NODES = {"Dense_2/Softmax"};
    private static final String OUTPUT_NODE = "Dense_2/Softmax";
    private static final long[] INPUT_SIZE = {1, 90, 3, 1};
    private static final int OUTPUT_SIZE = 6;

    public Classifier(final Context context) {
        try {
//            inferenceInterface = new TensorFlowInferenceInterface(context.getAssets(), MODEL_FILE);
        } catch (Exception e) {
            Log.d("Error Inference", e.getMessage());
        }
    }

    public float[] predictProbabilities(float[] data) {
        float[] result = new float[OUTPUT_SIZE];
//        inferenceInterface.feed(INPUT_NODE, data, INPUT_SIZE);
//        inferenceInterface.run(OUTPUT_NODES);
//        inferenceInterface.fetch(OUTPUT_NODE, result);
        return result;
    }

}
