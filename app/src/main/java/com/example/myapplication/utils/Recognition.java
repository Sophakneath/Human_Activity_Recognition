package com.example.myapplication.utils;

import androidx.annotation.NonNull;

// Recognition class for holding label, confidence, and id
public class Recognition {
    private final String id;
    private final String label;
    private final float confidence;

    public Recognition(String id, String label, float confidence) {
        this.id = id;
        this.label = label;
        this.confidence = confidence;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public float getConfidence() {
        return confidence;
    }

    @NonNull
    @Override
    public String toString() {
        return "Recognition{" +
                "id='" + id + '\'' +
                ", label='" + label + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}