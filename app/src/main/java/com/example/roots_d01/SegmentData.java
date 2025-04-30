package com.example.roots_d01;

import java.util.List;
import java.util.Collections;
// No need to import PolylinePoint if it's in the same package

// Helper class to hold data for a single loaded segment
public class SegmentData implements Comparable<SegmentData> {
    private List<PolylinePoint> points;
    private long startTime;
    private long endTime;
    private String originalFileName; // Optional: For debugging or advanced logic

    // Constructor
    public SegmentData(List<PolylinePoint> points, String fileName) {
        // Basic validation
        if (points == null || points.isEmpty()) {
            // Or handle this more gracefully depending on requirements
            throw new IllegalArgumentException("Points list cannot be null or empty for SegmentData.");
        }
        this.points = points;
        this.originalFileName = fileName; // Optional

        // Assuming points within the loaded file are generally chronological
        // If not strictly guaranteed, you might need to sort `points` by timestamp here first.
        this.startTime = points.get(0).timestamp;
        this.endTime = points.get(points.size() - 1).timestamp;
    }

    // --- Getters ---
    public List<PolylinePoint> getPoints() {
        return points;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public PolylinePoint getFirstPoint() {
        // Added check for safety, although constructor should prevent empty list
        return (points != null && !points.isEmpty()) ? points.get(0) : null;
    }

    public PolylinePoint getLastPoint() {
        // Added check for safety
        return (points != null && !points.isEmpty()) ? points.get(points.size() - 1) : null;
    }

    public String getOriginalFileName() { // Optional getter
        return originalFileName;
    }

    // --- Comparable Implementation ---
    // Allows sorting a list of SegmentData objects by their start time
    @Override
    public int compareTo(SegmentData other) {
        if (other == null) {
            return 1; // Consider nulls greater or handle as error
        }
        return Long.compare(this.startTime, other.startTime);
    }

    // Optional: Override toString() for easier debugging
    @Override
    public String toString() {
        return "SegmentData{" +
                "startTime=" + startTime +
                ", endTime=" + endTime +
                ", points=" + (points != null ? points.size() : 0) +
                ", file='" + originalFileName + '\'' +
                '}';
    }

    /**
     * Gets the representative transport mode for this segment.
     * Uses the mode of the first point, defaulting to "Unknown".
     * Assumes a segment primarily consists of one mode.
     * @return The transport mode string.
     */
    public String getRepresentativeMode() {
        if (points != null && !points.isEmpty() && points.get(0).transportMode != null && !points.get(0).transportMode.isEmpty()) {
            // Return the mode of the first point
            return points.get(0).transportMode;
        }
        // Return "Unknown" if points are missing or the first point has no mode
        return "Unknown";
    }
}