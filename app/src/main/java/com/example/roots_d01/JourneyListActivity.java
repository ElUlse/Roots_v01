package com.example.roots_d01;

import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AlertDialog; // For AlertDialog
import android.view.ViewGroup;          // For ViewGroup.LayoutParams

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;


public class JourneyListActivity extends AppCompatActivity implements com.example.roots_d01.JourneyAdapter.OnJourneyActionListener {

    private static final String TAG = "JourneyListActivity";
    private RecyclerView journeyRecyclerView;
    private com.example.roots_d01.JourneyAdapter journeyAdapter;
    private TextView emptyListTextView;
    private List<JourneyDetails> journeyDetailsList = new ArrayList<>();

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_journey_list);

        View rootView = findViewById(R.id.journeyListRootLayout);

        journeyRecyclerView = findViewById(R.id.journeyRecyclerView);
        emptyListTextView = findViewById(R.id.emptyListTextView);

        journeyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        journeyAdapter = new com.example.roots_d01.JourneyAdapter(this, new ArrayList<Object>(), this); // Pass new empty List<Object>
        journeyRecyclerView.setAdapter(journeyAdapter);
        // ---> Apply Insets Listener to the Root View <---
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                // Apply system bar insets as padding to the root view
                v.setPadding(
                        systemBarInsets.left,
                        systemBarInsets.top,    // Padding for status bar
                        systemBarInsets.right,
                        systemBarInsets.bottom  // Padding for navigation bar
                );
                Log.d(TAG, "Applied system bar insets as padding. Top: " + systemBarInsets.top + ", Bottom: " + systemBarInsets.bottom);
                // Return the insets unchanged (we've used them)
                return windowInsets;
            });
        } else {
            Log.e(TAG, "Root layout (journeyListRootLayout) not found!");
        }
        // ---> End Apply Insets <---
        // Defensive clear before loading
        journeyDetailsList.clear();
        journeyAdapter.notifyDataSetChanged();

        loadJourneysInBackground();
    }

    @Override
    public void onRenameRequested(JourneyDetails journey) {
        Log.d(TAG, "onRenameRequested for journey starting at: " + journey.startTimeMs);
        showRenameDialog(journey); // Call the method to show the dialog
    }
    private void showRenameDialog(JourneyDetails journeyToRename) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Journey");

        // Inflate a simple EditText layout or create it programmatically
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Enter new journey name");
        input.setText(journeyToRename.journeyName); // Pre-fill with current name
        input.selectAll(); // Select text for easy replacement
        // Add some padding to the EditText within the dialog
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new  android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (16 * getResources().getDisplayMetrics().density); // 16dp padding
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container); // Set container with padded EditText

        // Set up the buttons
        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(journeyToRename.journeyName)) {
                Log.d(TAG, "Attempting to rename journey " + journeyToRename.startTimeMs + " to: " + newName);
                updateJourneyName(journeyToRename, newName);
            } else if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "Name not changed."); // Name is the same
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }


    private void updateJourneyName(JourneyDetails journeyToUpdate, String newName) {
        // Find the journey in the list
        int index = -1;
        for(int i=0; i<journeyDetailsList.size(); i++) {
            // Compare by a reliable ID, startTimeMs is good here
            if (journeyDetailsList.get(i).startTimeMs == journeyToUpdate.startTimeMs) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            // Update the name in the list's data object
            journeyDetailsList.get(index).journeyName = newName;
            // Notify the adapter that *only this specific item* changed
            journeyAdapter.notifyItemChanged(index);
            Log.d(TAG, "Updated journey name in list and notified adapter at index: " + index);

            // Trigger saving the updated metadata in the background
            saveJourneyMetadataInBackground(journeyDetailsList.get(index)); // Pass the updated object
        } else {
            Log.e(TAG, "Could not find journey in list to update name for start time: " + journeyToUpdate.startTimeMs);
            Toast.makeText(this, "Error updating journey name", Toast.LENGTH_SHORT).show();
        }
    }

    // ADD this method to JourneyListActivity.java
    private void displayGroupedJourneys(List<JourneyDetails> loadedSegmentDetails) {
        if (loadedSegmentDetails == null) {
            loadedSegmentDetails = new ArrayList<>();
        }
        Log.d(TAG, "displayGroupedJourneys: Received " + loadedSegmentDetails.size() + " segments to group.");

        // 1. Sort segments (e.g., newest first) - Already sorted by filename/timestamp in loading usually, but can resort if needed
        // Collections.sort(loadedSegmentDetails, (j1, j2) -> Long.compare(j2.startTimeMs, j1.startTimeMs)); // Optional re-sort

        // 2. Create list for adapter (will hold Strings and JourneyDetails)
        List<Object> listItemsWithHeaders = new ArrayList<>();
        String lastHeaderDate = "";

        // Setup for date comparison/formatting
        Calendar cal = Calendar.getInstance();
        Calendar todayCal = Calendar.getInstance();
        Calendar yesterdayCal = Calendar.getInstance();
        yesterdayCal.add(Calendar.DATE, -1);
        SimpleDateFormat headerDateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()); // Example: April 20, 2025

        // 3. Iterate through sorted segments and insert headers
        for (JourneyDetails segmentDetails : loadedSegmentDetails) {
            if (segmentDetails == null || segmentDetails.startTimeMs <= 0) continue; // Skip invalid

            cal.setTimeInMillis(segmentDetails.startTimeMs);
            String currentDateHeader;

            if (cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)) {
                currentDateHeader = "Today";
            } else if (cal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)) {
                currentDateHeader = "Yesterday";
            } else {
                currentDateHeader = headerDateFormat.format(cal.getTime());
            }

            if (!currentDateHeader.equals(lastHeaderDate)) {
                listItemsWithHeaders.add(currentDateHeader); // Add String header
                lastHeaderDate = currentDateHeader;
                Log.d(TAG, "Adding header: " + currentDateHeader);
            }
            listItemsWithHeaders.add(segmentDetails); // Add JourneyDetails item
        }

        Log.i(TAG, "Finished grouping. List items with headers size: " + listItemsWithHeaders.size());


        // 4. Update the adapter
        if (journeyAdapter != null) {
            journeyAdapter.updateJourneys(listItemsWithHeaders); // Pass List<Object>

            // Update empty view visibility
            if (listItemsWithHeaders.isEmpty()) {
                emptyListTextView.setVisibility(View.VISIBLE);
                journeyRecyclerView.setVisibility(View.GONE);
                Log.d(TAG, "displayGroupedJourneys: Setting list to EMPTY VISIBLE");
            } else {
                emptyListTextView.setVisibility(View.GONE);
                journeyRecyclerView.setVisibility(View.VISIBLE);
                Log.d(TAG, "displayGroupedJourneys: Setting list to VISIBLE");

                // --- START: Scroll to Today Logic ---
                int todayIndex = -1;
                for (int i = 0; i < listItemsWithHeaders.size(); i++) {
                    Object item = listItemsWithHeaders.get(i);
                    // Check if the item is the "Today" header string
                    if (item instanceof String && "Today".equals(item)) {
                        todayIndex = i;
                        break; // Found the header
                    }
                    // Optional: If no "Today" header might exist,
                    // check if the first JourneyDetails item belongs to today
                    /* else if (item instanceof JourneyDetails && todayIndex == -1) {
                        Calendar itemCal = Calendar.getInstance();
                        itemCal.setTimeInMillis(((JourneyDetails) item).startTimeMs);
                        Calendar todayCal = Calendar.getInstance();
                        if (itemCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                            itemCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)) {
                            todayIndex = i; // Found first item of today
                            break;
                        }
                    }*/
                }

                if (todayIndex != -1) {
                    // Scroll to the found index
                    LinearLayoutManager layoutManager = (LinearLayoutManager) journeyRecyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        // Scrolls the item to the top of the view
                        layoutManager.scrollToPositionWithOffset(todayIndex, 0);
                        Log.i(TAG, "Scrolled RecyclerView to 'Today' section at index: " + todayIndex);
                    } else {
                        Log.e(TAG, "LayoutManager is null, cannot scroll.");
                    }
                } else {
                    Log.d(TAG, "No 'Today' section found in the list, not scrolling.");
                    // Optional: Scroll to top (index 0) if today isn't found?
                    // journeyRecyclerView.scrollToPosition(0);
                }
                // --- END: Scroll to Today Logic ---

            }
        } else {
            Log.e(TAG, "displayGroupedJourneys: journeyAdapter is null! Cannot display list.");
        }
    } // End of displayGroupedJourneys method
    private void saveJourneyMetadataInBackground(JourneyDetails details) {
        if (details == null) {
            Log.e(TAG, "saveJourneyMetadataInBackground (JourneyListActivity): Cannot save, details object is null.");
            return;
        }
        if (details.sourceFilenames == null || details.sourceFilenames.isEmpty()) {
            Log.e(TAG, "Cannot save metadata for journey " + details.startTimeMs + " (JourneyListActivity), sourceFilenames list is missing or empty.");
            // Optionally show toast
            return;
        }

        final JourneyMetadata metadataToSave = new JourneyMetadata(
                details.journeyName,
                details.sourceFilenames, // Make sure sourceFilenames is populated correctly during loading
                details.startTimeMs,
                details.mapMatched
        );
        final String metaFilename = "journey_meta_" + details.startTimeMs + ".json";
        Log.d(TAG, "saveJourneyMetadataInBackground (JourneyListActivity): Queuing save for " + metaFilename + " with matched status: " + metadataToSave.isMapMatched());

        if (metadataToSave.getSegmentFilenames() == null || metadataToSave.getSegmentFilenames().isEmpty()) {
            Log.e(TAG, "Cannot save metadata for journey " + details.startTimeMs + ", sourceFilenames list is missing or empty in JourneyDetails.");
            // Maybe show a toast error?
            // Toast.makeText(this, "Error saving name: Missing file info.", Toast.LENGTH_SHORT).show();
            return;
        }

        backgroundExecutor.execute(() -> {
            File directory = getExternalFilesDir(null);
            if (directory == null) {
                Log.e(TAG, "Cannot save metadata (JourneyListActivity): External directory is null.");
                mainThreadHandler.post(()-> Toast.makeText(JourneyListActivity.this, "Error accessing storage", Toast.LENGTH_SHORT).show());
                return;
            }
            File metaFile = new File(directory, metaFilename);

            try (java.io.FileWriter writer = new java.io.FileWriter(metaFile)) {
                // Use the created metadataToSave object
                gson.toJson(metadataToSave, writer);
                Log.i(TAG, "Successfully updated journey metadata in " + metaFilename + " (JourneyListActivity) with name: " + metadataToSave.getName() + ", Matched: " + metadataToSave.isMapMatched());
            } catch (Exception e) {
                Log.e(TAG, "Error saving updated journey metadata to " + metaFilename + " (JourneyListActivity)", e);
                // Optionally show error toast on main thread
                mainThreadHandler.post(()-> Toast.makeText(JourneyListActivity.this, "Error saving name", Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * Checks if a mode string represents a primary, user-initiated activity.
     */
    private boolean isPrimaryMode (String mode){
        if (mode == null) return false;
        switch (mode) {
            case "Walking":
            case "Bicycling":
            case "In Vehicle":
                return true;
            default:
                return false;
        }
    }

    /**
     * Checks if a mode string represents a transient or system-determined state
     * that might interrupt a primary activity but shouldn't necessarily split the journey.
     */
    private boolean isTransientMode (String mode){
        if (mode == null) return false;
        switch (mode) {
            case "Still":
                // Ensure LocationTrackingService is imported or use the string literal
            case "Unknown":
                // Add other modes like "Tilting" if necessary
                return true;
            default:
                return false;
        }
    }

    private void loadJourneysInBackground() {
        Log.d(TAG, "loadJourneysInBackground: Starting background load...");
        emptyListTextView.setVisibility(View.GONE);
        journeyRecyclerView.setVisibility(View.VISIBLE);

        backgroundExecutor.execute(() -> {
            File directory = getExternalFilesDir(null);
            if (directory != null) {
                Log.d(TAG, "Loading from directory: " + directory.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to get external files directory!");
                mainThreadHandler.post(() -> {
                    Toast.makeText(JourneyListActivity.this, "Error accessing storage", Toast.LENGTH_SHORT).show();
                    updateUiWithJourneys(new ArrayList<>()); // Update UI with empty list on error
                });
                return;
            }

            File[] files = directory.listFiles((dir, name) -> name.startsWith("polyline_data_") && name.endsWith(".json"));
            Log.d(TAG, "Found " + (files != null ? files.length : "null array or 0") + " polyline_data_*.json files.");

            List<SegmentData> loadedSegments = new ArrayList<>();
            if (files != null && files.length > 0) {
                for (File file : files) {
                    try (FileReader reader = new FileReader(file)) {
                        Type listType = new TypeToken<List<PolylinePoint>>() {}.getType();
                        List<PolylinePoint> loadedPoints = gson.fromJson(reader, listType);
                        if (loadedPoints != null && !loadedPoints.isEmpty()) {
                            loadedSegments.add(new SegmentData(loadedPoints, file.getName()));
                            // Log.d(TAG, "Successfully loaded SegmentData from: " + file.getName()); // Optional log
                        } else {
                            Log.w(TAG, "Loaded file " + file.getName() + " but points list was null or empty.");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading/parsing file " + file.getName(), e);
                    }
                }
                Collections.sort(loadedSegments); // Sort segments chronologically
            }
            Log.d(TAG, "Finished loading files. loadedSegments size: " + loadedSegments.size());

            // --- Intelligently join segments (Pass SegmentData list) ---
            final List<JourneyDetails> finalJourneys = processSegmentsIntoJourneys(loadedSegments);
            Log.d(TAG, "Finished processing segments. Final combined journeys count: " + finalJourneys.size());


            // --- Update UI on Main Thread ---
            mainThreadHandler.post(() -> {
                // Pass the FINAL list of COMBINED journeys
                updateUiWithJourneys(finalJourneys);
            });
        });
    }



    // Internal method to calculate details (includes accuracy)
    private JourneyDetails calculateJourneyDetailsInternal(List<PolylinePoint> journeyPoints, List<String> sourceFilenames) {
        Log.d(TAG, "calculateJourneyDetailsInternal: Method started for journey with " + (journeyPoints != null ? journeyPoints.size() : "null") + " points.");

        if (journeyPoints == null || journeyPoints.isEmpty()) {
            Log.w(TAG, "calculateJourneyDetailsInternal: Received null or empty points list.");
            // Pass 0f for average accuracy in the empty case
            return new JourneyDetails(0, 0, 0f,
                    new HashMap<String, Long>(),
                    new ArrayList<String>(),
                    new ArrayList<PolylinePoint>(),
                    null, 0f, false, null); // Pass 0f for accuracy
        }

        // --- Basic Details ---
        long startTime = journeyPoints.get(0).timestamp;
        long endTime = journeyPoints.get(journeyPoints.size() - 1).timestamp;

        // --- Calculate Distance, Duration per Mode, AND Average Accuracy ---
        float totalDistance = 0f;
        Map<String, Long> durationPerMode = new HashMap<>();
        float accuracySum = 0f; // <<< ADD
        int validAccuracyCount = 0; // <<< ADD
        Location lastLoc = null; // For distance calculation

        for (int i = 0; i < journeyPoints.size(); i++) {
            PolylinePoint p = journeyPoints.get(i);

            // Distance calculation (needs point i and i-1, handle first point)
            if (i > 0) {
                PolylinePoint pPrev = journeyPoints.get(i-1);
                totalDistance += calculateDistance(pPrev, p); // Use helper

                // Duration calculation (needs point i and i-1)
                long segmentDuration = p.timestamp - pPrev.timestamp;
                String segmentMode = (pPrev.transportMode != null && !pPrev.transportMode.isEmpty()) ? pPrev.transportMode : "Unknown";
                if (segmentDuration > 0) {
                    durationPerMode.put(segmentMode, durationPerMode.getOrDefault(segmentMode, 0L) + segmentDuration);
                }
            }

            // <<< ADD: Accuracy Calculation (process every point) >>>
            if (p.accuracy > 0) {
                accuracySum += p.accuracy;
                validAccuracyCount++;
                // Add log
                Log.d("AccuracyDebug", "JourneyListActivity.calculateJourneyDetailsInternal: Using Point Accuracy: " + p.accuracy);
            } else {
                // Add log
                Log.d("AccuracyDebug", "JourneyListActivity.calculateJourneyDetailsInternal: Skipping Point Accuracy (<=0): " + p.accuracy);
            }
            // <<< END Accuracy Calculation >>>
        }

        // Handle single point duration map case
        if (journeyPoints.size() == 1) {
            String singleMode = (journeyPoints.get(0).transportMode != null && !journeyPoints.get(0).transportMode.isEmpty()) ? journeyPoints.get(0).transportMode : "Unknown";
            durationPerMode.put(singleMode, 0L);
        }

        // <<< ADD: Calculate Average Accuracy >>>
        float averageAccuracy = (validAccuracyCount > 0) ? (accuracySum / validAccuracyCount) : 0f;
        // Add Log
        Log.d("AccuracyDebug", "JourneyListActivity.calculateJourneyDetailsInternal: Final Avg Accuracy=" + averageAccuracy + " (Sum=" + accuracySum + ", Count=" + validAccuracyCount + ")");
        // <<< END Calculate Average Accuracy >>>


        // --- Load Journey Name ---
        String loadedJourneyName = null;
        boolean loadedMapMatched = false;
        String loadedMatchedShape = null;
        Log.d(TAG, "calculateJourneyDetailsInternal: Attempting to load metadata for start time: " + startTime);

        // ... (Existing name loading logic using startTime, getExternalFilesDir, gson etc.) ...
        if (startTime > 0) {
            String metaFilename = "journey_meta_" + startTime + ".json";
            File directory = getExternalFilesDir(null);
            if (directory != null) {
                File metaFile = new File(directory, metaFilename);
                if (metaFile.exists()) {
                    try (FileReader reader = new FileReader(metaFile)) {
                        JourneyMetadata metadata = gson.fromJson(reader, JourneyMetadata.class);
                        if (metadata != null && metadata.getName() != null) {
                            loadedJourneyName = metadata.getName();
                            loadedMapMatched = metadata.isMapMatched();
                            loadedMatchedShape = metadata.getMatchedShape();
                            Log.d(TAG, "Loaded metadata from " + metaFilename + ": Name='" + loadedJourneyName + "', Matched=" + loadedMapMatched);
                        } else { /* Log warning */ }
                    } catch (Exception e) { /* Log error */ }
                } else { /* Log meta not found */ }
            } else { /* Log dir error */ }
        }
        // --- End Load Journey Name ---


        // *** FIX: Pass calculated averageAccuracy to the constructor ***
        return new JourneyDetails(startTime, endTime, totalDistance, durationPerMode,
                sourceFilenames, journeyPoints, loadedJourneyName, averageAccuracy, loadedMapMatched, loadedMatchedShape);
    }

    // Make sure the calculateDistance helper method also exists in JourneyListActivity
    private float calculateDistance(PolylinePoint p1, PolylinePoint p2) {
        // ... (Implementation as provided previously) ...
        if (p1 == null || p2 == null) return 0f; // Or handle appropriately
        float[] results = new float[1];
        try {
            android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results);
        } catch (IllegalArgumentException e) { return 0f; } // Or handle appropriately
        return results[0];
    }


    // Helper method to get color (can be shared or duplicated from MainActivity)
    public static int getColorForTransportMode(Context context, String transportMode) {
        // You'll need to load colors from SharedPreferences or use fixed colors here
        // Example using fixed colors for simplicity in this context:
        if (transportMode == null) return Color.DKGRAY;
        switch (transportMode) {
            case "Walking":    return Color.GREEN;
            case "Bicycling":  return Color.BLUE;
            case "In Vehicle": return Color.RED;
            default:           return Color.DKGRAY;
        }
        // --- OR ---
        // Load from SharedPreferences like in MainActivity/SettingsActivity
        // SharedPreferences prefs = context.getSharedPreferences("Settings", MODE_PRIVATE);
        // return prefs.getInt(getColorKeyForMode(transportMode), Color.DKGRAY);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
    }

    // Inside JourneyListActivity.java

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Reloading journey list data.");
    }


    private List<JourneyDetails> processSegmentsIntoJourneys(List<SegmentData> sortedSegments) {
        Log.d(TAG, "processSegmentsIntoJourneys: Processing " + (sortedSegments != null ? sortedSegments.size() : 0) + " segments.");
        List<JourneyDetails> finalProcessedJourneys = new ArrayList<>();
        if (sortedSegments == null || sortedSegments.isEmpty()) {
            return finalProcessedJourneys;
        }

        final long MAX_TIME_GAP_MS = 10 * 60 * 1000; // 10 minutes
        final float MAX_DISTANCE_GAP_METERS = 500.0f; //500 metres

        List<PolylinePoint> currentJourneyPoints = new ArrayList<>();
        List<String> currentJourneyFilenames = new ArrayList<>();
        SegmentData lastSegmentInCurrentJourney = null;

        for (SegmentData currentSegment : sortedSegments) {
            if (currentSegment == null || currentSegment.getPoints() == null || currentSegment.getPoints().isEmpty()) {
                Log.w(TAG, "Skipping null or empty segment in processSegmentsIntoJourneys.");
                continue;
            }

            boolean startNewJourney = true; // Assume starting new unless connection check passes

            if (lastSegmentInCurrentJourney != null) { // Check if there IS a previous segment
                PolylinePoint endPointPrevious = lastSegmentInCurrentJourney.getLastPoint();
                PolylinePoint startPointNext = currentSegment.getFirstPoint();

                if (endPointPrevious != null && startPointNext != null) {
                    long timeGap = startPointNext.timestamp - endPointPrevious.timestamp;
                    float distanceGap = calculateDistance(endPointPrevious, startPointNext);

                    // *** Get modes for comparison ***
                    String previousMode = lastSegmentInCurrentJourney.getRepresentativeMode(); // Uses helper
                    String nextMode = currentSegment.getRepresentativeMode();

                    boolean timeOK = timeGap >= 0 && timeGap <= MAX_TIME_GAP_MS;
                    boolean distOK = distanceGap <= MAX_DISTANCE_GAP_METERS;
                    boolean modeOK = !previousMode.equals("Unknown") && previousMode.equals(nextMode);
                    Log.d(TAG, "--> Connection Check Details:");
                    Log.d(TAG, "    Time Gap: " + timeGap + " ms (Max: " + MAX_TIME_GAP_MS + ") -> OK? " + timeOK);
                    Log.d(TAG, "    Dist Gap: " + distanceGap + " m (Max: " + MAX_DISTANCE_GAP_METERS + ") -> OK? " + distOK);
                    Log.d(TAG, "    Prev Mode: '" + previousMode + "', Next Mode: '" + nextMode + "' -> OK? " + modeOK);


                    Log.d(TAG, "Checking connection: PrevMode='" + previousMode + "', NextMode='" + nextMode + "', TimeGap=" + timeGap + "ms, DistGap=" + distanceGap + "m");

                    // Apply Connection Criteria (Time, Distance, AND a more lenient Mode check)
                    boolean timeAndDistanceOk = (timeGap >= 0 && timeGap <= MAX_TIME_GAP_MS &&
                            distanceGap <= MAX_DISTANCE_GAP_METERS);
                    boolean connect = false; // Default to not connecting

                    if (timeAndDistanceOk) {
                        // Connect if modes are identical and known
                        if (!previousMode.equals("Unknown") && previousMode.equals(nextMode)) {
                            connect = true;
                            Log.d(TAG, "  Decision Reason: Modes match directly.");
                        }
                        // OR Connect if one mode is primary and the other is transient/unknown
                        else if ( (isPrimaryMode(previousMode) && isTransientMode(nextMode)) ||
                                (isTransientMode(previousMode) && isPrimaryMode(nextMode)) ) {
                            connect = true;
                            Log.d(TAG, "  Decision Reason: Connecting primary mode with transient mode.");
                        }
                        // Optional: Connect if BOTH are transient? Maybe not desired.
                        // else if (isTransientMode(previousMode) && isTransientMode(nextMode)) {
                        //     connect = true;
                        //     Log.d(TAG, "  Decision Reason: Connecting two transient modes.");
                        // }
                        else {
                            Log.d(TAG, "  Decision Reason: Modes differ significantly or involve unhandled Unknown/Transient cases.");
                            connect = false;
                        }
                    } else {
                        Log.d(TAG, "  Decision Reason: Time or Distance gap too large.");
                        connect = false;
                    }

                    // *** Apply Connection Criteria (Time, Distance, AND Mode) ***
                    if (timeGap >= 0 && timeGap <= MAX_TIME_GAP_MS &&
                            distanceGap <= MAX_DISTANCE_GAP_METERS &&
                            !previousMode.equals("Unknown") && // Don't connect unknown modes
                            previousMode.equals(nextMode)) {   // Modes MUST match

                        startNewJourney = !connect;

                        Log.d(TAG, "--> Segments CONNECTED (Same Mode). Appending " + currentSegment.getOriginalFileName());
                    } else {
                        // Log reason for not connecting
                        if (!previousMode.equals(nextMode)) {
                            Log.d(TAG, "--> Segments NOT connected (Modes differ).");
                        } else if (timeGap > MAX_TIME_GAP_MS || distanceGap > MAX_DISTANCE_GAP_METERS) {
                            Log.d(TAG, "--> Segments NOT connected (Gap too large).");
                        } else {
                            Log.d(TAG, "--> Segments NOT connected (Unknown mode or other reason).");
                        }
                    }
                } else {
                    Log.w(TAG, "--> Segments NOT connected (Null endpoint).");
                }
            } else {
                // This is the very first segment, definitely start a new journey
                Log.d(TAG, "Starting first potential journey with segment: " + currentSegment.getOriginalFileName());
            }

            // Finalize previous journey if starting a new one
            if (startNewJourney && !currentJourneyPoints.isEmpty()) {
                // Calculate details for the *combined* points and filenames we collected
                JourneyDetails details = calculateJourneyDetailsInternal(currentJourneyPoints, currentJourneyFilenames);
                if (details != null) {
                    finalProcessedJourneys.add(details);
                    Log.d(TAG, "   Finalized journey [" + (finalProcessedJourneys.size()-1) + "] with " + currentJourneyPoints.size() + " points, " + currentJourneyFilenames.size() + " segments. Mode: " + details.getDominantMode());
                }
                // Reset for the new journey
                currentJourneyPoints = new ArrayList<>();
                currentJourneyFilenames = new ArrayList<>();
            }

            // Add current segment's data to the journey being built
            currentJourneyPoints.addAll(currentSegment.getPoints());
            currentJourneyFilenames.add(currentSegment.getOriginalFileName());
            lastSegmentInCurrentJourney = currentSegment; // Update the last segment added

        } // End loop

        // Add the very last journey being built
        if (!currentJourneyPoints.isEmpty()) {
            JourneyDetails details = calculateJourneyDetailsInternal(currentJourneyPoints, currentJourneyFilenames);
            if (details != null) {
                finalProcessedJourneys.add(details);
                Log.d(TAG, "Finalized LAST journey [" + (finalProcessedJourneys.size()-1) + "] with " + currentJourneyPoints.size() + " points, " + currentJourneyFilenames.size() + " segments. Mode: " + details.getDominantMode());
            }
        }

        Log.i(TAG,"Finished processing segments. Created " + finalProcessedJourneys.size() + " final journeys based on mode connection.");
        return finalProcessedJourneys;
    }


    /**
     * Updates the UI based on the processed list of journeys.
     * This is called from the background thread via the handler.
     * @param finalJourneys The final list of JourneyDetails objects (potentially combined).
     */
    private void updateUiWithJourneys(List<JourneyDetails> finalJourneys) {
        // Store the processed journeys in the activity's list variable
        // *** USE the correct variable name here: ***
        this.journeyDetailsList.clear();
        this.journeyDetailsList.addAll(finalJourneys);

        // Call the method that adds headers and updates the adapter
        // *** USE the correct variable name here: ***
        displayGroupedJourneys(this.journeyDetailsList);

        // Log the final state after the adapter should have been updated
        int adapterItemCount = (journeyAdapter != null) ? journeyAdapter.getItemCount() : -1;
        Log.d(TAG, "Final UI Update: Adapter getItemCount() = " + adapterItemCount);
        Log.d(TAG, "Final UI Update: emptyListTextView visibility = " +
                (emptyListTextView.getVisibility() == View.VISIBLE ? "VISIBLE" :
                        emptyListTextView.getVisibility() == View.GONE ? "GONE" : "INVISIBLE"));
        Log.d(TAG, "Final UI Update: journeyRecyclerView visibility = " +
                (journeyRecyclerView.getVisibility() == View.VISIBLE ? "VISIBLE" :
                        journeyRecyclerView.getVisibility() == View.GONE ? "GONE" : "INVISIBLE"));
    }
}