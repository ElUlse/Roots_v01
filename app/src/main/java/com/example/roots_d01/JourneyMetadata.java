package com.example.roots_d01;

import java.util.List;
import java.util.ArrayList;

// Simple class to store journey metadata for Gson serialization
public class JourneyMetadata {
    String name;
    List<String> segmentFilenames;
    long startTimeMs; // Store start time for potential identification
    boolean mapMatched = false;
    String matchedShape;
    // No-arg constructor for Gson
    public JourneyMetadata(String journeyName, List<String> sourceFilenames, long startTimeMs, boolean mapMatched) {
        this.segmentFilenames = new ArrayList<>();
    }

    public JourneyMetadata(String name, List<String> segmentFilenames, long startTimeMs, boolean mapMatched, String matchedShape) {
        this.name = name;
        // Ensure lists are initialized even if null is passed
        this.segmentFilenames = (segmentFilenames != null) ? segmentFilenames : new ArrayList<>();
        this.startTimeMs = startTimeMs;
        this.mapMatched = mapMatched;
        this.matchedShape = matchedShape; // Assign the matched shape
    }

    // Getters (Setters optional, Gson doesn't strictly need them for serialization)
    public String getName() { return name; }
    public List<String> getSegmentFilenames() { return segmentFilenames; }
    public long getStartTimeMs() { return startTimeMs; }
    public boolean isMapMatched() { return mapMatched; }

    public String getMatchedShape() { return matchedShape; }
    public void setMatchedShape(String shape) { this.matchedShape = shape; } // <-- ADD Setter
    public void setMapMatched(boolean matched) { this.mapMatched = matched; } // Ensure this exists

}