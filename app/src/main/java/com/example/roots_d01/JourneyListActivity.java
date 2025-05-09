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
import java.util.LinkedHashMap;
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
    private List<JourneyDetails> journeyDetailsList = new ArrayList<>(); // Stores ALL loaded journeys

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private Map<String, Boolean> headerExpansionStates = new LinkedHashMap<>();
    private boolean isInitialLoad = true; // Flag to manage initial expansion logic


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_journey_list);

        View rootView = findViewById(R.id.journeyListRootLayout);

        journeyRecyclerView = findViewById(R.id.journeyRecyclerView);
        emptyListTextView = findViewById(R.id.emptyListTextView);

        journeyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        journeyAdapter = new com.example.roots_d01.JourneyAdapter(this, new ArrayList<Object>(), this);
        journeyRecyclerView.setAdapter(journeyAdapter);

        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        systemBarInsets.left,
                        systemBarInsets.top,
                        systemBarInsets.right,
                        systemBarInsets.bottom
                );
                Log.d(TAG, "Applied system bar insets as padding. Top: " + systemBarInsets.top + ", Bottom: " + systemBarInsets.bottom);
                return windowInsets;
            });
        } else {
            Log.e(TAG, "Root layout (journeyListRootLayout) not found!");
        }

        journeyDetailsList.clear();
        if (journeyAdapter != null) {
            journeyAdapter.updateJourneys(new ArrayList<>(), new HashMap<>());
        }
        headerExpansionStates.clear();
        isInitialLoad = true;

        loadJourneysInBackground();
    }

    @Override
    public void onRenameRequested(JourneyDetails journey) {
        Log.d(TAG, "onRenameRequested for journey starting at: " + journey.startTimeMs);
        showRenameDialog(journey);
    }
    private void showRenameDialog(JourneyDetails journeyToRename) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Journey");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Enter new journey name");
        input.setText(journeyToRename.journeyName);
        input.selectAll();
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new  android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(journeyToRename.journeyName)) {
                Log.d(TAG, "Attempting to rename journey " + journeyToRename.startTimeMs + " to: " + newName);
                updateJourneyName(journeyToRename, newName);
            } else if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "Name not changed.");
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }


    private void updateJourneyName(JourneyDetails journeyToUpdate, String newName) {
        int indexInFullList = -1;
        for (int i = 0; i < this.journeyDetailsList.size(); i++) {
            if (this.journeyDetailsList.get(i).startTimeMs == journeyToUpdate.startTimeMs) {
                indexInFullList = i;
                break;
            }
        }

        if (indexInFullList != -1) {
            this.journeyDetailsList.get(indexInFullList).journeyName = newName;
            saveJourneyMetadataInBackground(this.journeyDetailsList.get(indexInFullList));
            isInitialLoad = false; // User interaction, not initial load
            displayGroupedJourneys(new ArrayList<>(this.journeyDetailsList), false); // MODIFIED: Pass false for scroll
            Log.d(TAG, "Updated journey name and refreshed list for start time: " + journeyToUpdate.startTimeMs);
            Toast.makeText(this, "Journey renamed", Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "Could not find journey in master list to update name for start time: " + journeyToUpdate.startTimeMs);
            Toast.makeText(this, "Error updating journey name", Toast.LENGTH_SHORT).show();
        }
    }

    // MODIFIED: Added boolean scrollToDefaultSection parameter
    private void displayGroupedJourneys(List<JourneyDetails> loadedSegmentDetails, boolean scrollToDefaultSection) {
        if (loadedSegmentDetails == null) {
            loadedSegmentDetails = new ArrayList<>();
        }
        Log.d(TAG, "displayGroupedJourneys: Received " + loadedSegmentDetails.size() + " segments (oldest first). isInitialLoad: " + isInitialLoad + ", scrollToDefault: " + scrollToDefaultSection);

        List<Object> listItemsWithHeaders = new ArrayList<>();
        String lastHeaderDateString = "";
        String todayHeaderString = "";
        String mostRecentDayHeaderString = "";

        Calendar cal = Calendar.getInstance();
        Calendar todayCal = Calendar.getInstance();
        Calendar yesterdayCal = Calendar.getInstance();
        yesterdayCal.add(Calendar.DATE, -1);
        SimpleDateFormat dayOfWeekFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        SimpleDateFormat headerDateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());

        if (isInitialLoad) { // This block now only sets default expansion states
            headerExpansionStates.clear();
            List<String> uniqueHeadersInOrder = new ArrayList<>();

            for (JourneyDetails segmentDetails : loadedSegmentDetails) {
                if (segmentDetails == null || segmentDetails.startTimeMs <= 0) continue;
                cal.setTimeInMillis(segmentDetails.startTimeMs);
                String currentHeaderKey;
                if (cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)) {
                    currentHeaderKey = "Today, " + dayOfWeekFormat.format(cal.getTime());
                    if (todayHeaderString.isEmpty()) {
                        todayHeaderString = currentHeaderKey;
                    }
                } else if (cal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)) {
                    currentHeaderKey = "Yesterday, " + dayOfWeekFormat.format(cal.getTime());
                } else {
                    currentHeaderKey = headerDateFormat.format(cal.getTime());
                }
                if (!uniqueHeadersInOrder.contains(currentHeaderKey)) {
                    uniqueHeadersInOrder.add(currentHeaderKey);
                }
            }

            if (!uniqueHeadersInOrder.isEmpty()) {
                mostRecentDayHeaderString = uniqueHeadersInOrder.get(uniqueHeadersInOrder.size() - 1);
            }

            String headerToExpand = !todayHeaderString.isEmpty() ? todayHeaderString : mostRecentDayHeaderString;

            for (String headerKey : uniqueHeadersInOrder) {
                headerExpansionStates.put(headerKey, headerKey.equals(headerToExpand));
            }
            if (uniqueHeadersInOrder.isEmpty()){
                Log.d(TAG, "No journeys, so no headers to set expansion state for.");
            } else if (!headerToExpand.isEmpty()) {
                Log.d(TAG, "Initial expansion: '" + headerToExpand + "' will be expanded.");
            } else {
                Log.w(TAG, "Initial expansion: No specific header identified for expansion (list might be empty or logic error).");
            }
        }

        for (JourneyDetails segmentDetails : loadedSegmentDetails) {
            if (segmentDetails == null || segmentDetails.startTimeMs <= 0) continue;

            cal.setTimeInMillis(segmentDetails.startTimeMs);
            String currentDateHeaderKey;
            if (cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)) {
                currentDateHeaderKey = "Today, " + dayOfWeekFormat.format(cal.getTime());
            } else if (cal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)) {
                currentDateHeaderKey = "Yesterday, " + dayOfWeekFormat.format(cal.getTime());
            } else {
                currentDateHeaderKey = headerDateFormat.format(cal.getTime());
            }

            if (!currentDateHeaderKey.equals(lastHeaderDateString)) {
                listItemsWithHeaders.add(currentDateHeaderKey);
                lastHeaderDateString = currentDateHeaderKey;
                headerExpansionStates.putIfAbsent(currentDateHeaderKey, false);
                Log.d(TAG, "Adding header to adapter list: " + currentDateHeaderKey + ", Expanded: " + headerExpansionStates.get(currentDateHeaderKey));
            }

            Boolean isHeaderExpandedCurrent = headerExpansionStates.get(currentDateHeaderKey); // Renamed to avoid conflict
            if (isHeaderExpandedCurrent != null && isHeaderExpandedCurrent) {
                listItemsWithHeaders.add(segmentDetails);
            }
        }

        Log.i(TAG, "Finished grouping. List items with headers size: " + listItemsWithHeaders.size());

        if (journeyAdapter != null) {
            journeyAdapter.updateJourneys(listItemsWithHeaders, headerExpansionStates);

            if (listItemsWithHeaders.isEmpty()) {
                emptyListTextView.setVisibility(View.VISIBLE);
                journeyRecyclerView.setVisibility(View.GONE);
            } else {
                emptyListTextView.setVisibility(View.GONE);
                journeyRecyclerView.setVisibility(View.VISIBLE);

                // MODIFIED: Conditional scrolling
                if (scrollToDefaultSection) {
                    final String finalTargetScrollHeader = !todayHeaderString.isEmpty() ? todayHeaderString : mostRecentDayHeaderString;
                    final String finalMostRecentDayHeaderString = mostRecentDayHeaderString; // Already effectively final if not reassigned
                    final String finalTodayHeaderString = todayHeaderString; // Already effectively final
                    final List<Object> finalListItemsForScroll = new ArrayList<>(listItemsWithHeaders);

                    int targetScrollIndex = -1;

                    if (!finalTargetScrollHeader.isEmpty()) {
                        for (int i = 0; i < finalListItemsForScroll.size(); i++) {
                            Object item = finalListItemsForScroll.get(i);
                            if (item instanceof String && item.equals(finalTargetScrollHeader)) {
                                targetScrollIndex = i;
                                break;
                            }
                        }
                    } else if (!finalListItemsForScroll.isEmpty() && finalListItemsForScroll.get(0) instanceof String) {
                        targetScrollIndex = 0;
                        Log.w(TAG, "No specific scroll target (Today/MostRecent), will scroll to top if list not empty.");
                    }

                    Log.d(TAG, "Attempting to scroll (scrollToDefaultSection=true). TargetHeader: '" + finalTargetScrollHeader + "', TargetIndex: " + targetScrollIndex);

                    if (targetScrollIndex != -1) {
                        LinearLayoutManager layoutManager = (LinearLayoutManager) journeyRecyclerView.getLayoutManager();
                        if (layoutManager != null) {
                            final int finalScrollIndex = targetScrollIndex;
                            journeyRecyclerView.postDelayed(() -> {
                                try {
                                    int currentItemCount = layoutManager.getItemCount();
                                    Log.d(TAG, "Inside postDelayed for scrolling. finalScrollIndex: " + finalScrollIndex + ", currentItemCount: " + currentItemCount);
                                    if (finalScrollIndex < currentItemCount) {
                                        boolean isTargetNearEndLambda = (finalListItemsForScroll.size() - finalScrollIndex) < 5;
                                        if (finalTargetScrollHeader.equals(finalMostRecentDayHeaderString) || finalTargetScrollHeader.equals(finalTodayHeaderString) || isTargetNearEndLambda) {
                                            layoutManager.scrollToPosition(finalListItemsForScroll.size() - 1);
                                            Log.i(TAG, "Posted and executed DELAYED scroll to END of list (target header: " + finalTargetScrollHeader + ").");
                                        } else {
                                            layoutManager.scrollToPositionWithOffset(finalScrollIndex, 0);
                                            Log.i(TAG, "Posted and executed DELAYED scroll to target header at index: " + finalScrollIndex);
                                        }
                                    } else {
                                        Log.w(TAG, "Scroll cancelled: finalScrollIndex (" + finalScrollIndex + ") is out of bounds for currentItemCount (" + currentItemCount + ").");
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Exception during delayed scroll execution", e);
                                }
                            }, 150);
                        } else {
                            Log.e(TAG, "LayoutManager is null, cannot scroll.");
                        }
                    } else {
                        Log.d(TAG, "No target header found for scrolling or list is empty (scrollToDefaultSection=true).");
                    }
                } else {
                    Log.d(TAG, "Not scrolling to default section (scrollToDefaultSection=false).");
                }
            }
        } else {
            Log.e(TAG, "displayGroupedJourneys: journeyAdapter is null! Cannot display list.");
        }
        if (scrollToDefaultSection) { // Only reset isInitialLoad if we performed the initial default setup
            isInitialLoad = false;
        }
    }

    public void onHeaderClicked(String headerDate) {
        Boolean currentState = headerExpansionStates.get(headerDate);
        if (currentState != null) {
            headerExpansionStates.put(headerDate, !currentState);
            Log.d(TAG, "Header '" + headerDate + "' toggled to: " + !currentState);
            isInitialLoad = false; // User interaction, not initial load
            if (this.journeyDetailsList != null) {
                displayGroupedJourneys(new ArrayList<>(this.journeyDetailsList), false); // MODIFIED: Pass false for scroll
            }
        }
    }

    private void saveJourneyMetadataInBackground(JourneyDetails details) {
        if (details == null) {
            Log.e(TAG, "saveJourneyMetadataInBackground (JourneyListActivity): Cannot save, details object is null.");
            return;
        }
        if (details.sourceFilenames == null || details.sourceFilenames.isEmpty()) {
            Log.e(TAG, "Cannot save metadata for journey " + details.startTimeMs + " (JourneyListActivity), sourceFilenames list is missing or empty.");
            return;
        }

        final JourneyMetadata metadataToSave = new JourneyMetadata(
                details.journeyName,
                details.sourceFilenames,
                details.startTimeMs,
                details.mapMatched,
                details.matchedShape
        );
        final String metaFilename = "journey_meta_" + details.startTimeMs + ".json";
        Log.d(TAG, "saveJourneyMetadataInBackground (JourneyListActivity): Queuing save for " + metaFilename +
                " with Name: " + metadataToSave.getName() +
                ", Matched: " + metadataToSave.isMapMatched() +
                ", Shape: " + (metadataToSave.getMatchedShape() != null ? "Present" : "Null"));


        backgroundExecutor.execute(() -> {
            File directory = getExternalFilesDir(null);
            if (directory == null) {
                Log.e(TAG, "Cannot save metadata (JourneyListActivity): External directory is null.");
                mainThreadHandler.post(()-> Toast.makeText(JourneyListActivity.this, "Error accessing storage", Toast.LENGTH_SHORT).show());
                return;
            }
            File metaFile = new File(directory, metaFilename);

            try (java.io.FileWriter writer = new java.io.FileWriter(metaFile)) {
                gson.toJson(metadataToSave, writer);
                Log.i(TAG, "Successfully updated journey metadata in " + metaFilename + " (JourneyListActivity) with name: " + metadataToSave.getName() + ", Matched: " + metadataToSave.isMapMatched());
            } catch (Exception e) {
                Log.e(TAG, "Error saving updated journey metadata to " + metaFilename + " (JourneyListActivity)", e);
                mainThreadHandler.post(()-> Toast.makeText(JourneyListActivity.this, "Error saving name", Toast.LENGTH_SHORT).show());
            }
        });
    }

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

    private boolean isTransientMode (String mode){
        if (mode == null) return false;
        switch (mode) {
            case "Still":
            case "Unknown":
                return true;
            default:
                return false;
        }
    }

    private void loadJourneysInBackground() {
        Log.d(TAG, "loadJourneysInBackground: Starting background load...");
        isInitialLoad = true;

        backgroundExecutor.execute(() -> {
            File directory = getExternalFilesDir(null);
            if (directory != null) {
                Log.d(TAG, "Loading from directory: " + directory.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to get external files directory!");
                mainThreadHandler.post(() -> {
                    Toast.makeText(JourneyListActivity.this, "Error accessing storage", Toast.LENGTH_SHORT).show();
                    updateUiWithJourneys(new ArrayList<>());
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
                        } else {
                            Log.w(TAG, "Loaded file " + file.getName() + " but points list was null or empty.");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading/parsing file " + file.getName(), e);
                    }
                }
                Collections.sort(loadedSegments, (s1, s2) -> Long.compare(s1.getStartTime(), s2.getStartTime()));
                Log.d(TAG, "Sorted loadedSegments OLDEST first.");
            }
            Log.d(TAG, "Finished loading files. loadedSegments size: " + loadedSegments.size());

            final List<JourneyDetails> finalJourneys = processSegmentsIntoJourneys(loadedSegments);
            Log.d(TAG, "Finished processing segments. Final combined journeys count: " + finalJourneys.size());

            mainThreadHandler.post(() -> {
                updateUiWithJourneys(finalJourneys);
            });
        });
    }


    private JourneyDetails calculateJourneyDetailsInternal(List<PolylinePoint> journeyPoints, List<String> sourceFilenames) {
        Log.d(TAG, "calculateJourneyDetailsInternal: Method started for journey with " + (journeyPoints != null ? journeyPoints.size() : "null") + " points.");

        if (journeyPoints == null || journeyPoints.isEmpty()) {
            Log.w(TAG, "calculateJourneyDetailsInternal: Received null or empty points list.");
            return new JourneyDetails(0, 0, 0f,
                    new HashMap<String, Long>(),
                    new ArrayList<String>(),
                    new ArrayList<PolylinePoint>(),
                    null, 0f, false, null);
        }

        long startTime = journeyPoints.get(0).timestamp;
        long endTime = journeyPoints.get(journeyPoints.size() - 1).timestamp;

        float totalDistance = 0f;
        Map<String, Long> durationPerMode = new HashMap<>();
        float accuracySum = 0f;
        int validAccuracyCount = 0;

        for (int i = 0; i < journeyPoints.size(); i++) {
            PolylinePoint p = journeyPoints.get(i);
            if (p == null) continue;

            if (i > 0) {
                PolylinePoint pPrev = journeyPoints.get(i-1);
                if (pPrev != null) {
                    totalDistance += calculateDistance(pPrev, p);
                    long segmentDuration = p.timestamp - pPrev.timestamp;
                    String segmentMode = (pPrev.transportMode != null && !pPrev.transportMode.isEmpty()) ? pPrev.transportMode : "Unknown";
                    if (segmentDuration > 0) {
                        durationPerMode.put(segmentMode, durationPerMode.getOrDefault(segmentMode, 0L) + segmentDuration);
                    }
                }
            }

            if (p.accuracy > 0) {
                accuracySum += p.accuracy;
                validAccuracyCount++;
            }
        }

        if (journeyPoints.size() == 1) {
            PolylinePoint singleP = journeyPoints.get(0);
            if (singleP != null) {
                String singleMode = (singleP.transportMode != null && !singleP.transportMode.isEmpty()) ? singleP.transportMode : "Unknown";
                durationPerMode.put(singleMode, 0L);
            }
        }

        float averageAccuracy = (validAccuracyCount > 0) ? (accuracySum / validAccuracyCount) : 0f;
        Log.d("AccuracyDebug", "JourneyListActivity.calculateJourneyDetailsInternal: Final Avg Accuracy=" + averageAccuracy + " (Sum=" + accuracySum + ", Count=" + validAccuracyCount + ")");

        String loadedJourneyName = null;
        boolean loadedMapMatched = false;
        String loadedMatchedShape = null;

        if (startTime > 0) {
            String metaFilename = "journey_meta_" + startTime + ".json";
            File directory = getExternalFilesDir(null);
            if (directory != null) {
                File metaFile = new File(directory, metaFilename);
                if (metaFile.exists()) {
                    try (FileReader reader = new FileReader(metaFile)) {
                        JourneyMetadata metadata = gson.fromJson(reader, JourneyMetadata.class);
                        if (metadata != null) {
                            loadedJourneyName = metadata.getName();
                            loadedMapMatched = metadata.isMapMatched();
                            loadedMatchedShape = metadata.getMatchedShape();
                            Log.d(TAG, "Loaded metadata from " + metaFilename + ": Name='" + loadedJourneyName + "', Matched=" + loadedMapMatched);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading metadata file " + metaFilename, e);
                    }
                }
            }
        }
        return new JourneyDetails(startTime, endTime, totalDistance, durationPerMode,
                sourceFilenames, journeyPoints, loadedJourneyName, averageAccuracy, loadedMapMatched, loadedMatchedShape);
    }

    private float calculateDistance(PolylinePoint p1, PolylinePoint p2) {
        if (p1 == null || p2 == null) return 0f;
        float[] results = new float[1];
        try {
            Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results);
        } catch (IllegalArgumentException e) { return 0f; }
        return results[0];
    }

    public static int getColorForTransportMode(Context context, String transportMode) {
        if (transportMode == null) return Color.DKGRAY;
        switch (transportMode) {
            case "Walking":    return Color.GREEN;
            case "Bicycling":  return Color.BLUE;
            case "In Vehicle": return Color.RED;
            default:           return Color.DKGRAY;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Triggering reload of journey list data.");
        isInitialLoad = true; // Reset for default collapse/scroll on resume
        loadJourneysInBackground();
    }


    private List<JourneyDetails> processSegmentsIntoJourneys(List<SegmentData> sortedSegments) {
        Log.d(TAG, "processSegmentsIntoJourneys: Processing " + (sortedSegments != null ? sortedSegments.size() : 0) + " segments (oldest first).");
        List<JourneyDetails> finalProcessedJourneys = new ArrayList<>();
        if (sortedSegments == null || sortedSegments.isEmpty()) {
            return finalProcessedJourneys;
        }

        final long MAX_TIME_GAP_MS = 10 * 60 * 1000;
        final float MAX_DISTANCE_GAP_METERS = 500.0f;

        List<PolylinePoint> currentJourneyPoints = new ArrayList<>();
        List<String> currentJourneyFilenames = new ArrayList<>();
        SegmentData lastSegmentInCurrentJourney = null;

        for (SegmentData currentSegment : sortedSegments) {
            if (currentSegment == null || currentSegment.getPoints() == null || currentSegment.getPoints().isEmpty()) {
                Log.w(TAG, "Skipping null or empty segment in processSegmentsIntoJourneys.");
                continue;
            }

            boolean startNewJourney = true;

            if (lastSegmentInCurrentJourney != null) {
                PolylinePoint endPointPrevious = lastSegmentInCurrentJourney.getLastPoint();
                PolylinePoint startPointNext = currentSegment.getFirstPoint();

                if (endPointPrevious != null && startPointNext != null) {
                    long timeGap = startPointNext.timestamp - endPointPrevious.timestamp;
                    float distanceGap = calculateDistance(endPointPrevious, startPointNext);
                    String previousMode = lastSegmentInCurrentJourney.getRepresentativeMode();
                    String nextMode = currentSegment.getRepresentativeMode();

                    boolean timeAndDistanceOk = (timeGap >= 0 && timeGap <= MAX_TIME_GAP_MS &&
                            distanceGap <= MAX_DISTANCE_GAP_METERS);
                    boolean connect = false;

                    if (timeAndDistanceOk) {
                        if (!previousMode.equals("Unknown") && previousMode.equals(nextMode)) {
                            connect = true;
                        } else if ((isPrimaryMode(previousMode) && isTransientMode(nextMode)) ||
                                (isTransientMode(previousMode) && isPrimaryMode(nextMode))) {
                            connect = true;
                        }
                    }
                    startNewJourney = !connect;
                }
            }

            if (startNewJourney && !currentJourneyPoints.isEmpty()) {
                JourneyDetails details = calculateJourneyDetailsInternal(currentJourneyPoints, currentJourneyFilenames);
                if (details != null) {
                    finalProcessedJourneys.add(details);
                }
                currentJourneyPoints = new ArrayList<>();
                currentJourneyFilenames = new ArrayList<>();
            }

            currentJourneyPoints.addAll(currentSegment.getPoints());
            currentJourneyFilenames.add(currentSegment.getOriginalFileName());
            lastSegmentInCurrentJourney = currentSegment;
        }

        if (!currentJourneyPoints.isEmpty()) {
            JourneyDetails details = calculateJourneyDetailsInternal(currentJourneyPoints, currentJourneyFilenames);
            if (details != null) {
                finalProcessedJourneys.add(details);
            }
        }
        Log.i(TAG,"Finished processing segments. Created " + finalProcessedJourneys.size() + " final journeys (oldest first).");
        return finalProcessedJourneys;
    }

    private void updateUiWithJourneys(List<JourneyDetails> finalJourneys) {
        this.journeyDetailsList.clear();
        if (finalJourneys != null) {
            this.journeyDetailsList.addAll(finalJourneys);
        }
        // Pass true for scrollToDefaultSection only when updating UI after initial load or full refresh
        displayGroupedJourneys(new ArrayList<>(this.journeyDetailsList), true); // MODIFIED

        int adapterItemCount = (journeyAdapter != null) ? journeyAdapter.getItemCount() : -1;
        Log.d(TAG, "Final UI Update: Adapter getItemCount() = " + adapterItemCount +
                ", emptyListTextView visibility = " + (emptyListTextView.getVisibility() == View.VISIBLE ? "VISIBLE" : "GONE") +
                ", journeyRecyclerView visibility = " + (journeyRecyclerView.getVisibility() == View.VISIBLE ? "VISIBLE" : "GONE"));
    }
}