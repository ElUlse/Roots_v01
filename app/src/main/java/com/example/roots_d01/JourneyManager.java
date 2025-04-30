package com.example.roots_d01;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class JourneyManager {

    private static final String TAG = "JourneyManager";
    private static final String TAG_LOAD = "PolylineLoadDebug";

    private final Context context;
    private final ExecutorService backgroundExecutor;
    private final Handler mainThreadHandler;
    private final Gson gson;
    private final JourneyLoadListener loadListener;
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private static final float MIN_JOURNEY_SPAN_METERS = 150.0f; //150 metres

    // Listener interface for load completion
    public interface JourneyLoadListener {
        void onJourneysLoaded(List<SegmentData> sortedSegments, boolean forceRematch);
        void onJourneyLoadError(String errorMessage);
    }

    // Constructor
    public JourneyManager(Context context, ExecutorService backgroundExecutor, Handler mainThreadHandler, Gson gson, JourneyLoadListener listener) {
        this.context = context.getApplicationContext();
        this.backgroundExecutor = backgroundExecutor;
        this.mainThreadHandler = mainThreadHandler;
        // If Gson is simple, create new; otherwise, pass shared instance
        this.gson = (gson != null) ? gson : new Gson();
        this.loadListener = listener;
        Log.d(TAG, "JourneyManager initialized.");
    }

    /**
     * Loads all polyline segment data files from storage, filters out segments
     * with insufficient geographical span (deleting them), sorts the remaining
     * segments by start time, and notifies the listener upon completion.
     * Runs file operations on a background thread.
     */
    public void loadAllPolylineData() {
        Log.d(TAG_LOAD, "loadAllPolylineData called...");

        // Prevent concurrent loading operations
        if (!isLoading.compareAndSet(false, true)) {
            Log.w(TAG_LOAD, "Load already in progress. Skipping redundant call.");
            return;
        }

        Log.d(TAG_LOAD, "JourneyManager: Initiating background load/filter of polyline data...");

        backgroundExecutor.execute(() -> {
            boolean forceRematch = false;
            List<SegmentData> loadedSegments = new ArrayList<>();
            File directory = null;

            try {
                // Check for force rematch flag from settings
                SharedPreferences prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
                forceRematch = prefs.getBoolean("force_rematch_on_next_load", false);
                if (forceRematch) {
                    Log.i(TAG_LOAD, "JourneyManager (BG): Force rematch flag is SET.");
                    prefs.edit().putBoolean("force_rematch_on_next_load", false).apply(); // Reset flag
                }

                // Get storage directory
                directory = context.getExternalFilesDir(null);
                if (directory == null) {
                    Log.e(TAG_LOAD, "Failed to get external files directory in background task.");
                    notifyError("Error accessing storage");
                    return; // Exit task early
                }

                // List potential segment files
                File[] files = directory.listFiles((dir, name) -> name.startsWith("polyline_data_") && name.endsWith(".json"));

                // Load data from each file
                if (files != null && files.length > 0) {
                    Log.d(TAG_LOAD, "JourneyManager (BG): Found " + files.length + " potential polyline files.");
                    for (File file : files) {
                        Log.v(TAG_LOAD, "Considering segment file: " + file.getName());
                        try (FileReader reader = new FileReader(file)) {
                            Type listType = new TypeToken<List<PolylinePoint>>() {}.getType();
                            List<PolylinePoint> loadedPoints = gson.fromJson(reader, listType);

                            if (loadedPoints != null && !loadedPoints.isEmpty()) {
                                try {
                                    SegmentData segment = new SegmentData(loadedPoints, file.getName());
                                    loadedSegments.add(segment);
                                } catch (IllegalArgumentException e) {
                                    Log.w(TAG_LOAD, "Skipping segment file due to issue during SegmentData creation: " + file.getName() + " - " + e.getMessage());
                                }
                            } else {
                                Log.w(TAG_LOAD, "Skipping empty or invalid JSON content in file: " + file.getName());
                            }
                        } catch (Exception e) {
                            Log.e(TAG_LOAD, "Error processing file " + file.getName(), e);
                            // Optionally delete corrupted files here?
                        }
                    } // End file loop
                    Log.d(TAG_LOAD, "JourneyManager (BG): Finished initial loading of " + loadedSegments.size() + " segments.");

                    // Sort the loaded segments chronologically
                    try {
                        Collections.sort(loadedSegments);
                        Log.d(TAG_LOAD, "JourneyManager (BG): Sorted " + loadedSegments.size() + " segments.");
                    } catch (Exception sortEx) {
                        Log.e(TAG_LOAD, "Error sorting loaded segments", sortEx);
                        notifyError("Error sorting journey data");
                        return; // Exit task if sorting fails
                    }

                } else if (files == null) {
                    Log.e(TAG_LOAD, "Error listing files in directory (BG): " + directory.getAbsolutePath());
                    notifyError("Error reading storage");
                    // No need to proceed further if listing failed
                    return;
                } else { // files.length == 0
                    Log.i(TAG_LOAD, "JourneyManager (BG): No polyline files found matching pattern.");
                    // Proceed with empty list to notify listener
                }

                // --- Filter and Delete Segments with Small Span ---
                List<SegmentData> segmentsToKeep = new ArrayList<>();
                List<SegmentData> segmentsToDelete = new ArrayList<>();

                for (SegmentData segment : loadedSegments) {
                    float maxSpan = calculateMaxSpanDistance(segment.getPoints());
                    if (maxSpan < MIN_JOURNEY_SPAN_METERS) {
                        Log.i(TAG_LOAD, "Segment " + segment.getOriginalFileName() + " marked for deletion (Max Span: " + String.format("%.1f", maxSpan) + "m < " + MIN_JOURNEY_SPAN_METERS + "m)");
                        segmentsToDelete.add(segment);
                    } else {
                        segmentsToKeep.add(segment);
                    }
                }

                // --- Perform Deletion (still in background) ---
                if (!segmentsToDelete.isEmpty()) {
                    Log.i(TAG_LOAD, "Deleting " + segmentsToDelete.size() + " segments with insufficient span...");
                    for (SegmentData segment : segmentsToDelete) {
                        // Delete JSON data file
                        File fileToDelete = new File(directory, segment.getOriginalFileName());
                        if (fileToDelete.exists()) {
                            if (fileToDelete.delete()) {
                                Log.d(TAG_LOAD, "Deleted segment file: " + fileToDelete.getName());
                            } else {
                                Log.w(TAG_LOAD, "Failed to delete segment file: " + fileToDelete.getName());
                            }
                        } else {
                            Log.w(TAG_LOAD, "Segment file not found for deletion: " + fileToDelete.getName());
                        }

                        // Delete corresponding metadata file
                        String metaFilename = "journey_meta_" + segment.getStartTime() + ".json";
                        File metaFileToDelete = new File(directory, metaFilename);
                        if (metaFileToDelete.exists()) {
                            if (metaFileToDelete.delete()) {
                                Log.d(TAG_LOAD, "Deleted metadata file: " + metaFileToDelete.getName());
                            } else {
                                Log.w(TAG_LOAD, "Failed to delete metadata file: " + metaFileToDelete.getName());
                            }
                        }
                        // No need to log if meta file doesn't exist, it might not have been created yet
                    }
                    Log.i(TAG_LOAD, "Finished deleting segments.");
                }

                // --- Notify Listener with the FILTERED list ---
                Log.i(TAG_LOAD, "JourneyManager (BG): Notifying listener with " + segmentsToKeep.size() + " segments remaining.");
                notifySuccess(segmentsToKeep, forceRematch); // Pass the filtered list

            } catch (Exception e) {
                Log.e(TAG_LOAD, "Unexpected error during background load/filter task", e);
                notifyError("Unexpected error loading data");
            } finally {
                isLoading.set(false); // Reset the flag when task finishes (success or error)
                Log.d(TAG_LOAD, "Background load/filter task finished. isLoading reset to false.");
            }
        }); // End backgroundExecutor task
    }
    // --- End Method: loadAllPolylineData ---

    // --- Helper methods to notify listener on main thread ---
    private void notifySuccess(List<SegmentData> result, boolean forceRematch) {
        final List<SegmentData> finalResult = (result != null) ? new ArrayList<>(result) : new ArrayList<>();
        // Pass the forceRematch parameter correctly to the listener inside the runnable
        mainThreadHandler.post(() -> {
            if (loadListener != null) {
                // Use the forceRematch parameter passed into notifySuccess
                loadListener.onJourneysLoaded(finalResult, forceRematch);
            }
        });
    }

    private void notifyError(String message) {
        mainThreadHandler.post(() -> {
            if (loadListener != null) {
                loadListener.onJourneyLoadError(message);
            }
        });
    }

    /**
     * Calculates the maximum straight-line distance between the first point and any subsequent point.
     * @param points List of PolylinePoints for the segment/journey.
     * @return The maximum distance in meters, or 0 if points are null/empty/single.
     */
    private static float calculateMaxSpanDistance(List<PolylinePoint> points) {
        if (points == null || points.size() < 2) {
            return 0f;
        }

        PolylinePoint startPoint = points.get(0);
        float maxDistance = 0f;
        float[] results = new float[1]; // Reusable array for distanceBetween

        for (int i = 1; i < points.size(); i++) {
            PolylinePoint currentPoint = points.get(i);
            try {
                Location.distanceBetween(
                        startPoint.latitude, startPoint.longitude,
                        currentPoint.latitude, currentPoint.longitude,
                        results);
                if (results[0] > maxDistance) {
                    maxDistance = results[0];
                }
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Error calculating distance for max span check", e);
                // Ignore this point for max distance calculation
            }
        }
        return maxDistance;
    }

    // --- End Helper methods ---

    // Add processSegmentsIntoJourneys and other methods here later...

} // End of JourneyManager class