package com.example.roots_d01.network; // Use your actual package name

import android.util.Log;

import java.util.List;

// Represents the overall request body sent to Valhalla
public class ValhallaRequest {
    // The list of coordinates defining the path to be matched
    List<ShapePoint> shape;

    // Specifies the routing profile (e.g., "auto", "bicycle", "pedestrian")
    String costing = "auto";

    // Optional parameters for map matching
    // *** Needs to be public ***
    public TraceOptions trace_options;

    // Optional parameter controlling matching algorithm
    String shape_match = "map_snap"; // Default for potentially noisy GPS data

    public void setCosting(String costing) {
        // Optional validation: only set if not null/empty? Or allow any string?
        if (costing != null && !costing.isEmpty()) {
            this.costing = costing;
            Log.d("ValhallaRequest", "Costing set to: " + this.costing); // Optional log
        } else {
            Log.w("ValhallaRequest", "Attempted to set null or empty costing, keeping default: " + this.costing);
            // Or you could default back to "auto": this.costing = "auto";
        }
    }

    // Constructor
    public ValhallaRequest(List<ShapePoint> shape) {
        this.shape = shape;
        this.trace_options = new TraceOptions(); // Initialize default options
    }

    /**
     * Sets the search radius within the trace options.
     * @param radius The search radius in meters.
     */
    public void setSearchRadius(int radius) {
        if (this.trace_options != null) {
            // Accessing search_radius is okay IF TraceOptions is public
            // and search_radius within TraceOptions is public
            this.trace_options.search_radius = radius;
        } else {
            System.err.println("Warning: trace_options is null in ValhallaRequest when trying to set search radius.");
        }
    }

    // --- Inner class for individual shape points ---
    public static class ShapePoint {
        double lat;
        double lon;
        public ShapePoint(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    // --- Inner class for trace options ---
    // *** Correctly made public static ***
    public static class TraceOptions {
        // *** Ensure fields are public ***
        public int search_radius = 50;
        public double gps_accuracy; // Now public (and uncommented)

        // Other options you might add based on Valhalla docs:
        // public double breakage_distance; // Make public if used
        // public double interpolation_distance; // Make public if used
    }
}