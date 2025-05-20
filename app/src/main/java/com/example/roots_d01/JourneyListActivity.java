package com.example.roots_d01;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.maplibre.android.MapLibre;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.snapshotter.MapSnapshotter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class JourneyListActivity extends AppCompatActivity implements JourneyAdapter.OnJourneyActionListener {

    private static final String TAG = "JourneyListActivity";
    private RecyclerView journeyRecyclerView;
    private JourneyAdapter journeyAdapter;
    private TextView emptyListTextView;
    private List<JourneyDetails> journeyDetailsList = new ArrayList<>();

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private Map<String, Boolean> headerExpansionStates = new LinkedHashMap<>();
    private boolean isInitialLoad = true;
    private Map<Long, String> journeyPreviewFilePaths = new HashMap<>();
    private static final String PREVIEW_SUBDIR = "journey_previews";
    private final AtomicBoolean isLoadingJourneys = new AtomicBoolean(false);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            MapLibre.getInstance(this.getApplicationContext());
            Log.i(TAG, "MapLibre SDK initialized successfully in JourneyListActivity.");
        } catch (Exception e) {
            Log.e(TAG, "FATAL: Error initializing MapLibre SDK in JourneyListActivity", e);
            Toast.makeText(this, "Critical error: Map components failed to initialize.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_journey_list);

        View rootView = findViewById(R.id.journeyListRootLayout);
        journeyRecyclerView = findViewById(R.id.journeyRecyclerView);
        emptyListTextView = findViewById(R.id.emptyListTextView);

        if (journeyRecyclerView == null || emptyListTextView == null) {
            Log.e(TAG, "Critical UI elements (RecyclerView or EmptyTextView) not found. Aborting onCreate.");
            Toast.makeText(this, "Error initializing list view layout.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        journeyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        journeyAdapter = new JourneyAdapter(this, new ArrayList<>(), this, backgroundExecutor, mainThreadHandler);
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
            Log.w(TAG, "Root layout (journeyListRootLayout) not found for insets.");
        }

        journeyDetailsList.clear();
        headerExpansionStates.clear();
        journeyPreviewFilePaths.clear();
        isInitialLoad = true;

        loadJourneysInBackground();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Checking if journey load is needed.");
        // isInitialLoad will be reset inside loadJourneysInBackground if it proceeds
        loadJourneysInBackground();
    }


    @Override
    public void onRenameRequested(JourneyDetails journey) {
        Log.d(TAG, "onRenameRequested for journey starting at: " + journey.startTimeMs);
        showRenameDialog(journey);
    }

    private void showRenameDialog(JourneyDetails journeyToRename) {
        if (journeyToRename == null) {
            Log.e(TAG, "showRenameDialog: journeyToRename is null.");
            return;
        }
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
        if (journeyToUpdate == null) {
            Log.e(TAG, "updateJourneyName: journeyToUpdate is null.");
            return;
        }
        for (int i = 0; i < this.journeyDetailsList.size(); i++) {
            if (this.journeyDetailsList.get(i).startTimeMs == journeyToUpdate.startTimeMs) {
                indexInFullList = i;
                break;
            }
        }

        if (indexInFullList != -1) {
            this.journeyDetailsList.get(indexInFullList).journeyName = newName;
            saveJourneyMetadataInBackground(this.journeyDetailsList.get(indexInFullList));
            isInitialLoad = false;
            displayGroupedJourneysWithPreviews(new ArrayList<>(this.journeyDetailsList), false);
            Log.d(TAG, "Updated journey name and refreshed list for start time: " + journeyToUpdate.startTimeMs);
            Toast.makeText(this, "Journey renamed", Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "Could not find journey in master list to update name for start time: " + journeyToUpdate.startTimeMs);
            Toast.makeText(this, "Error updating journey name", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayGroupedJourneysWithPreviews(List<JourneyDetails> journeysToDisplay, boolean scrollToDefault) {
        Log.d(TAG, "displayGroupedJourneysWithPreviews: Received " + (journeysToDisplay != null ? journeysToDisplay.size() : "null") + " journeys. Scroll: " + scrollToDefault);
        List<Object> listItemsWithHeaders = new ArrayList<>();

        String lastWeekHeaderKey = "";
        String lastDayHeaderKey = "";
        Calendar journeyCal = Calendar.getInstance();
        Calendar todayCal = Calendar.getInstance();
        Calendar yesterdayCal = Calendar.getInstance();
        yesterdayCal.add(Calendar.DATE, -1);
        SimpleDateFormat dayOfWeekFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        SimpleDateFormat dayHeaderFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()); // Corrected year format
        SimpleDateFormat weekHeaderFormat = new SimpleDateFormat("'Week of' MMMM d, yyyy", Locale.getDefault()); // Corrected year format
        Map<String, Set<String>> modesPerDay = new HashMap<>();
        String todayDayHeaderKeyForExpansion = "";
        String mostRecentDayHeaderKeyForExpansion = "";


        if (journeysToDisplay != null) {
            for (JourneyDetails journey : journeysToDisplay) {
                if (journey == null || journey.startTimeMs <= 0) continue;
                journeyCal.setTimeInMillis(journey.startTimeMs);
                String currentDayDisplayHeader;
                if (isSameDay(journeyCal, todayCal)) {
                    currentDayDisplayHeader = "Today, " + dayOfWeekFormat.format(journeyCal.getTime());
                    if (todayDayHeaderKeyForExpansion.isEmpty() && isInitialLoad) todayDayHeaderKeyForExpansion = currentDayDisplayHeader;
                } else if (isSameDay(journeyCal, yesterdayCal)) {
                    currentDayDisplayHeader = "Yesterday, " + dayOfWeekFormat.format(journeyCal.getTime());
                } else {
                    currentDayDisplayHeader = dayHeaderFormat.format(journeyCal.getTime());
                }
                modesPerDay.putIfAbsent(currentDayDisplayHeader, new HashSet<>());
                if (journey.durationPerModeMs != null) {
                    for (String mode : journey.durationPerModeMs.keySet()) {
                        if (journey.durationPerModeMs.getOrDefault(mode, 0L) > 0 && !mode.equals("Still") && !mode.equals("Unknown")) {
                            modesPerDay.get(currentDayDisplayHeader).add(mode);
                        }
                    }
                }
            }
        }

        if (isInitialLoad && journeysToDisplay != null && !journeysToDisplay.isEmpty()) {
            if (todayDayHeaderKeyForExpansion.isEmpty()) {
                List<JourneyDetails> reversedJourneys = new ArrayList<>(journeysToDisplay);
                Collections.reverse(reversedJourneys);
                for(JourneyDetails journey : reversedJourneys) {
                    if (journey == null || journey.startTimeMs <= 0) continue;
                    journeyCal.setTimeInMillis(journey.startTimeMs);
                    if (isSameDay(journeyCal, todayCal)) { /* Already handled by todayDayHeaderKeyForExpansion */ }
                    else if (isSameDay(journeyCal, yesterdayCal)) { mostRecentDayHeaderKeyForExpansion = "Yesterday, " + dayOfWeekFormat.format(journeyCal.getTime()); break; }
                    else { mostRecentDayHeaderKeyForExpansion = dayHeaderFormat.format(journeyCal.getTime()); break; }
                }
            }
            if (mostRecentDayHeaderKeyForExpansion.isEmpty() && !journeysToDisplay.isEmpty()) {
                JourneyDetails lastJourneyInOriginalOrder = journeysToDisplay.get(journeysToDisplay.size() - 1); // Original order is oldest first, so last is newest
                if (lastJourneyInOriginalOrder != null && lastJourneyInOriginalOrder.startTimeMs > 0) {
                    journeyCal.setTimeInMillis(lastJourneyInOriginalOrder.startTimeMs);
                    if (isSameDay(journeyCal, todayCal)) mostRecentDayHeaderKeyForExpansion = "Today, " + dayOfWeekFormat.format(journeyCal.getTime());
                    else if (isSameDay(journeyCal, yesterdayCal)) mostRecentDayHeaderKeyForExpansion = "Yesterday, " + dayOfWeekFormat.format(journeyCal.getTime());
                    else mostRecentDayHeaderKeyForExpansion = dayHeaderFormat.format(journeyCal.getTime());
                }
            }
        }


        String dayHeaderToExpandInitially = !todayDayHeaderKeyForExpansion.isEmpty() ? todayDayHeaderKeyForExpansion : mostRecentDayHeaderKeyForExpansion;
        if(isInitialLoad && dayHeaderToExpandInitially.isEmpty() && journeysToDisplay != null && !journeysToDisplay.isEmpty()){
            // Fallback if still empty: use the header of the very first journey in the (already reversed for display) list
            JourneyDetails firstDisplayJourney = journeysToDisplay.get(0);
            if (firstDisplayJourney != null && firstDisplayJourney.startTimeMs > 0) {
                journeyCal.setTimeInMillis(firstDisplayJourney.startTimeMs);
                if (isSameDay(journeyCal, todayCal)) dayHeaderToExpandInitially = "Today, " + dayOfWeekFormat.format(journeyCal.getTime());
                else if (isSameDay(journeyCal, yesterdayCal)) dayHeaderToExpandInitially = "Yesterday, " + dayOfWeekFormat.format(journeyCal.getTime());
                else dayHeaderToExpandInitially = dayHeaderFormat.format(journeyCal.getTime());
            }
        }


        for (JourneyDetails journey : journeysToDisplay) {
            if (journey == null || journey.startTimeMs <= 0) continue;
            journeyCal.setTimeInMillis(journey.startTimeMs);
            Calendar weekStartCal = (Calendar) journeyCal.clone();
            weekStartCal.setFirstDayOfWeek(Calendar.MONDAY);
            weekStartCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            weekStartCal.set(Calendar.HOUR_OF_DAY, 0); weekStartCal.set(Calendar.MINUTE, 0); weekStartCal.set(Calendar.SECOND, 0); weekStartCal.set(Calendar.MILLISECOND, 0);
            String currentWeekHeaderKey = weekHeaderFormat.format(weekStartCal.getTime());

            String currentDayDisplayHeader;
            if (isSameDay(journeyCal, todayCal)) currentDayDisplayHeader = "Today, " + dayOfWeekFormat.format(journeyCal.getTime());
            else if (isSameDay(journeyCal, yesterdayCal)) currentDayDisplayHeader = "Yesterday, " + dayOfWeekFormat.format(journeyCal.getTime());
            else currentDayDisplayHeader = dayHeaderFormat.format(journeyCal.getTime());
            String currentDayExpansionKey = currentDayDisplayHeader;

            if (!currentWeekHeaderKey.equals(lastWeekHeaderKey)) {
                listItemsWithHeaders.add(currentWeekHeaderKey);
                lastWeekHeaderKey = currentWeekHeaderKey;
                lastDayHeaderKey = "";
            }
            if (!currentDayExpansionKey.equals(lastDayHeaderKey)) {
                Set<String> modesForThisDay = modesPerDay.getOrDefault(currentDayDisplayHeader, new HashSet<>());
                DayHeaderItem dayHeader = new DayHeaderItem(currentDayDisplayHeader, modesForThisDay);
                listItemsWithHeaders.add(dayHeader);
                lastDayHeaderKey = currentDayExpansionKey;
                if (isInitialLoad) {
                    headerExpansionStates.put(currentDayExpansionKey, currentDayExpansionKey.equals(dayHeaderToExpandInitially));
                } else {
                    headerExpansionStates.putIfAbsent(currentDayExpansionKey, false);
                }
            }
            Boolean isDayHeaderExpanded = headerExpansionStates.get(currentDayExpansionKey);
            if (isDayHeaderExpanded != null && isDayHeaderExpanded) {
                listItemsWithHeaders.add(journey);
            }
        }

        if (journeyAdapter != null) {
            journeyAdapter.updateJourneysWithPreviews(listItemsWithHeaders, headerExpansionStates, this.journeyPreviewFilePaths);

            if (listItemsWithHeaders.isEmpty()) {
                emptyListTextView.setVisibility(View.VISIBLE);
                journeyRecyclerView.setVisibility(View.GONE);
            } else {
                emptyListTextView.setVisibility(View.GONE);
                journeyRecyclerView.setVisibility(View.VISIBLE);
                if (scrollToDefault && isInitialLoad) {
                    int targetScrollIndex = -1;
                    if (!dayHeaderToExpandInitially.isEmpty()) {
                        for (int i = 0; i < listItemsWithHeaders.size(); i++) {
                            Object item = listItemsWithHeaders.get(i);
                            if (item instanceof DayHeaderItem && ((DayHeaderItem) item).dateHeaderText.equals(dayHeaderToExpandInitially)) {
                                targetScrollIndex = i;
                                break;
                            }
                        }
                    } else if (!listItemsWithHeaders.isEmpty()){
                        targetScrollIndex = 0; // Default to top if no specific day header found to expand
                        for (int i = 0; i < listItemsWithHeaders.size(); i++) {
                            if (listItemsWithHeaders.get(i) instanceof DayHeaderItem) {targetScrollIndex = i; break;}
                        }
                    }

                    LinearLayoutManager layoutManager = (LinearLayoutManager) journeyRecyclerView.getLayoutManager();
                    if (layoutManager != null && targetScrollIndex != -1 && targetScrollIndex < listItemsWithHeaders.size()) {
                        final int finalScrollIndex = targetScrollIndex;
                        journeyRecyclerView.postDelayed(() -> {
                            try {
                                if (finalScrollIndex < layoutManager.getItemCount()) {
                                    layoutManager.scrollToPositionWithOffset(finalScrollIndex, 0);
                                    Log.i(TAG, "Scrolled to initial section at index: " + finalScrollIndex);
                                } else {
                                    Log.w(TAG, "Delayed scroll: finalScrollIndex " + finalScrollIndex + " out of bounds for item count " + layoutManager.getItemCount());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Exception during delayed scroll", e);
                            }
                        }, 150);
                    }
                }
            }
        }
        if (isInitialLoad) {
            isInitialLoad = false;
        }
    }


    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            return false;
        }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    public void onHeaderClicked(String headerDate) {
        Boolean currentState = headerExpansionStates.get(headerDate);
        if (currentState != null) {
            headerExpansionStates.put(headerDate, !currentState);
            Log.d(TAG, "Header '" + headerDate + "' toggled to: " + !currentState);
            isInitialLoad = false;
            if (this.journeyDetailsList != null) {
                displayGroupedJourneysWithPreviews(new ArrayList<>(this.journeyDetailsList), false);
            }
        }
    }

    private void saveJourneyMetadataInBackground(JourneyDetails details) {
        if (details == null) {
            Log.e(TAG, "saveJourneyMetadataInBackground : Cannot save, details object is null.");
            return;
        }
        if (details.sourceFilenames == null || details.sourceFilenames.isEmpty()) {
            Log.e(TAG, "Cannot save metadata for journey " + details.startTimeMs + ", sourceFilenames list is missing or empty.");
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
        Log.d(TAG, "saveJourneyMetadataInBackground: Queuing save for " + metaFilename +
                " with Name: " + metadataToSave.getName() +
                ", Matched: " + metadataToSave.isMapMatched() +
                ", Shape: " + (metadataToSave.getMatchedShape() != null ? "Present" : "Null"));

        backgroundExecutor.execute(() -> {
            File directory = getFilesDir();
            if (directory == null) {
                Log.e(TAG, "Cannot save metadata: Internal files directory is null.");
                mainThreadHandler.post(()-> Toast.makeText(JourneyListActivity.this, "Error accessing storage for saving metadata", Toast.LENGTH_SHORT).show());
                return;
            }

            File metaFile = new File(directory, metaFilename);

            try (java.io.FileWriter writer = new java.io.FileWriter(metaFile)) {
                gson.toJson(metadataToSave, writer);
                Log.i(TAG, "Successfully updated journey metadata in " + metaFile.getAbsolutePath() + " with name: " + metadataToSave.getName() + ", Matched: " + metadataToSave.isMapMatched());
            } catch (Exception e) {
                Log.e(TAG, "Error saving updated journey metadata to " + metaFile.getAbsolutePath(), e);
                mainThreadHandler.post(()-> Toast.makeText(JourneyListActivity.this, "Error saving journey name", Toast.LENGTH_SHORT).show());
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
        if (isLoadingJourneys.getAndSet(true)) {
            Log.d(TAG, "loadJourneysInBackground: Already loading. Skipping.");
            return;
        }
        Log.i(TAG, "loadJourneysInBackground: Starting background load and preview generation...");
        isInitialLoad = true;

        journeyPreviewFilePaths.clear();

        backgroundExecutor.execute(() -> {
            try {
                File segmentDataDirectory = getExternalFilesDir(null);
                if (segmentDataDirectory == null) {
                    Log.w(TAG, "External files directory is null. Trying internal for segments as fallback.");
                    segmentDataDirectory = getFilesDir();
                    if (segmentDataDirectory == null) {
                        Log.e(TAG, "Failed to get any suitable directory for loading segments!");
                        mainThreadHandler.post(() -> {
                            Toast.makeText(JourneyListActivity.this, "Error accessing storage for journey data", Toast.LENGTH_SHORT).show();
                            updateUiWithJourneys(new ArrayList<>());
                        });
                        isLoadingJourneys.set(false);
                        return;
                    }
                }

                List<SegmentData> loadedSegments = new ArrayList<>();
                File[] files = segmentDataDirectory.listFiles((dir, name) -> name.startsWith("polyline_data_") && name.endsWith(".json"));
                if (files != null && files.length > 0) {
                    for (File file : files) {
                        try (FileReader reader = new FileReader(file)) {
                            Type listType = new TypeToken<List<PolylinePoint>>() {}.getType();
                            List<PolylinePoint> pointsList = gson.fromJson(reader, listType);
                            if (pointsList != null && !pointsList.isEmpty()) {
                                loadedSegments.add(new SegmentData(pointsList, file.getName()));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error loading/parsing segment file " + file.getName(), e);
                        }
                    }
                    Collections.sort(loadedSegments);
                    Log.d(TAG, "Loaded and sorted " + loadedSegments.size() + " segments from " + segmentDataDirectory.getAbsolutePath());
                } else {
                    Log.i(TAG, "No polyline_data files found in " + segmentDataDirectory.getAbsolutePath());
                }

                final List<JourneyDetails> finalJourneys = processSegmentsIntoJourneys(loadedSegments);
                Log.d(TAG, "Processed into " + finalJourneys.size() + " final journeys.");

                if (!finalJourneys.isEmpty()) {
                    Log.d(TAG, "Starting snapshot generation for " + finalJourneys.size() + " journeys...");
                    for (JourneyDetails journey : finalJourneys) {
                        if (journey == null) {
                            Log.w(TAG, "Null journey encountered during snapshot generation loop. Skipping.");
                            continue;
                        }
                        generateAndSaveMapPreviewSynchronously(journey);
                    }
                    Log.d(TAG, "Finished snapshot generation attempts.");
                }

                mainThreadHandler.post(() -> {
                    updateUiWithJourneys(finalJourneys);
                });
            } catch (Exception e) {
                Log.e(TAG, "Unhandled exception in loadJourneysInBackground", e);
                mainThreadHandler.post(() -> Toast.makeText(JourneyListActivity.this, "Error loading journeys", Toast.LENGTH_SHORT).show());
            } finally {
                isLoadingJourneys.set(false);
                Log.i(TAG, "loadJourneysInBackground: Background task finished.");
            }
        });
    }

    // In JourneyListActivity.java

    private void generateAndSaveMapPreviewSynchronously(JourneyDetails journey) {
        if (journey == null || journey.points == null || journey.points.isEmpty()) {
            Log.w(TAG, "Skipping preview generation for null or empty journey (ID: " + (journey != null ? journey.startTimeMs : "null") + ")");
            return;
        }

        File internalFilesDir = getFilesDir();
        if (internalFilesDir == null) {
            Log.e(TAG, "Failed to get internal files directory for previews.");
            return;
        }
        File previewDir = new File(internalFilesDir, PREVIEW_SUBDIR);
        if (!previewDir.exists()) {
            if (!previewDir.mkdirs()) {
                Log.e(TAG, "Failed to create preview directory: " + previewDir.getAbsolutePath());
                return;
            }
        }
        File previewFile = new File(previewDir, "preview_journey_" + journey.startTimeMs + ".png");

        if (previewFile.exists()) {
            Log.d(TAG, "Preview already exists for journey " + journey.startTimeMs + ": " + previewFile.getAbsolutePath());
            journeyPreviewFilePaths.put(journey.startTimeMs, previewFile.getAbsolutePath());
            return;
        }

        List<PolylinePoint> points = journey.points;

        // --- MODIFIED BOUNDS CALCULATION START ---
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        boolean hasAtLeastOneValidPoint = false;

        for (PolylinePoint p : points) {
            if (p != null) {
                minLat = Math.min(minLat, p.latitude);
                maxLat = Math.max(maxLat, p.latitude);
                minLon = Math.min(minLon, p.longitude);
                maxLon = Math.max(maxLon, p.longitude);
                hasAtLeastOneValidPoint = true;
            }
        }

        if (!hasAtLeastOneValidPoint) {
            Log.w(TAG, "Skipping preview for journey " + journey.startTimeMs + ": no valid points found for bounds calculation.");
            return;
        }

        double latSpan = maxLat - minLat;
        double lonSpan = maxLon - minLon;
        double paddingFactor = 0.20; // Increased padding to 20% for better visibility

        // Ensure a minimum geographic span for very short journeys or single points
        double minSpanDegrees = 0.001; // Approx 110 meters. Adjust if needed.
        // If journeys are often very short, make this smaller (e.g., 0.0005)
        // If they are longer, this can be larger.

        if (Math.abs(latSpan) < 1E-6 && Math.abs(lonSpan) < 1E-6) { // Essentially a single point
            minLat -= minSpanDegrees / 2.0;
            maxLat += minSpanDegrees / 2.0;
            minLon -= minSpanDegrees / 2.0;
            maxLon += minSpanDegrees / 2.0;
        } else {
            if (latSpan < minSpanDegrees) {
                double midLat = (minLat + maxLat) / 2.0;
                minLat = midLat - (minSpanDegrees / 2.0);
                maxLat = midLat + (minSpanDegrees / 2.0);
            }
            if (lonSpan < minSpanDegrees) {
                double midLon = (minLon + maxLon) / 2.0;
                minLon = midLon - (minSpanDegrees / 2.0);
                maxLon = midLon + (minSpanDegrees / 2.0);
            }
        }

        // Recalculate spans after ensuring minimum
        latSpan = maxLat - minLat;
        lonSpan = maxLon - minLon;

        // Apply padding to the calculated spans
        minLat -= latSpan * paddingFactor;
        maxLat += latSpan * paddingFactor;
        minLon -= lonSpan * paddingFactor;
        maxLon += lonSpan * paddingFactor;

        LatLngBounds journeyBoundsWithPadding;
        try {
            // Ensure NorthEast is (maxLat, maxLon) and SouthWest is (minLat, minLon)
            journeyBoundsWithPadding = new LatLngBounds.Builder()
                    .include(new LatLng(maxLat, maxLon))
                    .include(new LatLng(minLat, minLon))
                    .build();
            Log.d(TAG, "Calculated padded bounds for journey " + journey.startTimeMs + ": NE(" + maxLat + "," + maxLon + "), SW(" + minLat + "," + minLon + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error building padded LatLngBounds for journey " + journey.startTimeMs, e);
            // Fallback for safety, though the above logic should prevent most IllegalStateExceptions
            if (!points.isEmpty() && points.get(0) != null) {
                PolylinePoint firstPoint = points.get(0);
                double fallbackOffset = 0.002; // Slightly larger fallback
                journeyBoundsWithPadding = new LatLngBounds.Builder()
                        .include(new LatLng(firstPoint.latitude + fallbackOffset, firstPoint.longitude + fallbackOffset))
                        .include(new LatLng(firstPoint.latitude - fallbackOffset, firstPoint.longitude - fallbackOffset))
                        .build();
                Log.w(TAG, "Using fallback bounds for journey " + journey.startTimeMs);
            } else {
                Log.e(TAG, "Cannot create fallback bounds for journey " + journey.startTimeMs);
                return;
            }
        }
        // --- MODIFIED BOUNDS CALCULATION END ---


        int previewWidthPx = (int) (300 * getResources().getDisplayMetrics().density);
        int previewHeightPx = Math.round(180 * getResources().getDisplayMetrics().density);

        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        String styleIdentifier = prefs.getString(SettingsActivity.KEY_MAP_STYLE_IDENTIFIER, SettingsActivity.DEFAULT_MAP_STYLE);
        String styleUrl = MapManager.getStyleUrl(styleIdentifier);
        if (styleUrl == null) {
            Log.w(TAG, "Map style URL is null for snapshot. Using default. Identifier: " + styleIdentifier);
            styleUrl = MapManager.getStyleUrl(SettingsActivity.DEFAULT_MAP_STYLE);
            if (styleUrl == null) {
                Log.e(TAG, "Default Map style URL is also null. Using absolute fallback for snapshot.");
                styleUrl = "https://demotiles.maplibre.org/style.json"; // Absolute fallback
            }
        }

        final MapSnapshotter.Options options = new MapSnapshotter.Options(previewWidthPx, previewHeightPx)
                .withStyle(styleUrl)
                .withRegion(journeyBoundsWithPadding) // *** USE THE PADDED BOUNDS HERE ***
                .withLogo(false);

        final CountDownLatch latch = new CountDownLatch(1);
        final Bitmap[] snapshotResult = new Bitmap[1];
        final String[] snapshotErrorString = new String[1];

        Log.d(TAG, "Preparing to start snapshot for journey " + journey.startTimeMs + " on UI thread with region: " + journeyBoundsWithPadding);

        final String finalStyleUrl = styleUrl; // For use in lambda
        mainThreadHandler.post(() -> {
            try {
                Log.d(TAG, "Instantiating MapSnapshotter on UI thread for journey " + journey.startTimeMs + " with style: " + finalStyleUrl);
                MapSnapshotter snapshotter = new MapSnapshotter(getApplicationContext(), options);
                Log.d(TAG, "Starting snapshot on UI thread for journey " + journey.startTimeMs);
                snapshotter.start(snapshot -> {
                    if (snapshot != null) {
                        snapshotResult[0] = snapshot.getBitmap();
                        Log.d(TAG, "Snapshot received (UI thread) for journey " + journey.startTimeMs);
                    } else {
                        Log.e(TAG, "Snapshot object is null (UI thread) for journey " + journey.startTimeMs);
                    }
                    latch.countDown();
                }, errorMsg -> {
                    snapshotErrorString[0] = errorMsg;
                    Log.e(TAG, "MapSnapshotter error (UI thread) for journey " + journey.startTimeMs + ": " + errorMsg);
                    latch.countDown();
                });
            } catch (Exception e) {
                Log.e(TAG, "Exception when trying to start MapSnapshotter on UI thread for " + journey.startTimeMs, e);
                snapshotErrorString[0] = "Snapshotter init/start failed: " + e.getMessage();
                latch.countDown();
            }
        });

        try {
            Log.d(TAG, "Background thread waiting for snapshot latch for journey " + journey.startTimeMs);
            latch.await(); // Wait for the snapshot to complete on the UI thread
            Log.d(TAG, "Background thread latch released for journey " + journey.startTimeMs);
        } catch (InterruptedException e) {
            Log.e(TAG, "Snapshot generation interrupted while waiting for latch (journey " + journey.startTimeMs + ")", e);
            Thread.currentThread().interrupt();
            return;
        }

        if (snapshotErrorString[0] != null || snapshotResult[0] == null) {
            Log.e(TAG, "Failed to generate basemap snapshot for journey " + journey.startTimeMs + ". Error: " + (snapshotErrorString[0] != null ? snapshotErrorString[0] : "Snapshot bitmap was null"));
            // Consider creating a placeholder/error image or skipping
            return;
        }

        Bitmap basemapBitmap = snapshotResult[0];
        Bitmap mutableBasemap = null;
        try {
            mutableBasemap = basemapBitmap.copy(Bitmap.Config.ARGB_8888, true);
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OutOfMemoryError copying basemap bitmap for journey " + journey.startTimeMs, oom);
            if (basemapBitmap != null && !basemapBitmap.isRecycled()) {
                basemapBitmap.recycle();
            }
            return;
        } finally {
            // Recycle the original snapshot from MapSnapshotter if a copy was made or if copy failed
            if (basemapBitmap != null && !basemapBitmap.isRecycled()) {
                if (mutableBasemap != basemapBitmap) { // Only if copy succeeded and is different
                    basemapBitmap.recycle();
                    Log.d(TAG, "Recycled original snapshot bitmap after copy for journey " + journey.startTimeMs);
                } else if (mutableBasemap == null) { // If copy failed
                    basemapBitmap.recycle();
                    Log.d(TAG, "Recycled original snapshot bitmap as copy failed for journey " + journey.startTimeMs);
                }
            }
        }

        if (mutableBasemap == null) {
            Log.e(TAG, "Mutable basemap is null, cannot draw polyline for journey " + journey.startTimeMs);
            return;
        }

        int polylineColor = JourneyListActivity.getColorForTransportMode(this, journey.getDominantMode());
        float polylineWidthPx = 3f * getResources().getDisplayMetrics().density; // Example: 3dp polyline width

        // Now, MapPreviewGenerator draws onto the basemap that should already be correctly framed.
        Bitmap finalPreview = MapPreviewGenerator.drawPolylineOnBasemap(mutableBasemap, points, polylineColor, polylineWidthPx);

        if (finalPreview != null) {
            try (FileOutputStream out = new FileOutputStream(previewFile)) {
                finalPreview.compress(Bitmap.CompressFormat.PNG, 90, out);
                Log.i(TAG, "Saved final preview for journey " + journey.startTimeMs + " to: " + previewFile.getAbsolutePath());
                journeyPreviewFilePaths.put(journey.startTimeMs, previewFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "Error saving final preview for journey " + journey.startTimeMs, e);
            } catch (Exception e) { // Catch any other unexpected errors during save
                Log.e(TAG, "Unexpected error saving final preview for " + journey.startTimeMs, e);
            } finally {
                if (finalPreview != null && !finalPreview.isRecycled()) {
                    finalPreview.recycle();
                    Log.d(TAG, "Recycled final preview bitmap for journey " + journey.startTimeMs);
                }
            }
        } else {
            Log.e(TAG, "Failed to draw polyline on basemap for journey " + journey.startTimeMs + " (finalPreview was null).");
            // If finalPreview is null, it means drawPolylineOnBasemap returned null,
            // mutableBasemap was potentially its input if it wasn't modified in place.
            if (mutableBasemap != null && !mutableBasemap.isRecycled()) {
                mutableBasemap.recycle();
                Log.d(TAG, "Recycled mutableBasemap as finalPreview was null for journey " + journey.startTimeMs);
            }
        }
    }

// Make sure the rest of JourneyListActivity.java remains the same.

    private void updateUiWithJourneys(List<JourneyDetails> finalJourneys) {
        this.journeyDetailsList.clear();
        if (finalJourneys != null) {
            this.journeyDetailsList.addAll(finalJourneys);
        }
        displayGroupedJourneysWithPreviews(new ArrayList<>(this.journeyDetailsList), isInitialLoad);
    }


    private JourneyDetails calculateJourneyDetailsInternal(List<PolylinePoint> journeyPoints, List<String> sourceFilenames) {
        if (journeyPoints == null || journeyPoints.isEmpty()) {
            Log.w(TAG, "calculateJourneyDetailsInternal: Received null or empty points list.");
            return new JourneyDetails(0, 0, 0f,
                    new HashMap<>(), new ArrayList<>(), new ArrayList<>(),
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

        float averageAccuracy = (validAccuracyCount > 0) ? (accuracySum / validAccuracyCount) : -1f;
        Log.d("AccuracyDebug", "JourneyListActivity.calcDetailsInternal: AvgAcc=" + averageAccuracy);

        String loadedJourneyName = null;
        boolean loadedMapMatched = false;
        String loadedMatchedShape = null;

        if (startTime > 0) {
            String metaFilename = "journey_meta_" + startTime + ".json";
            File directory = getExternalFilesDir(null);
            if (directory == null) directory = getFilesDir();


            if (directory != null) {
                File metaFile = new File(directory, metaFilename);
                if (metaFile.exists()) {
                    try (FileReader reader = new FileReader(metaFile)) {
                        JourneyMetadata metadata = gson.fromJson(reader, JourneyMetadata.class);
                        if (metadata != null) {
                            loadedJourneyName = metadata.getName();
                            loadedMapMatched = metadata.isMapMatched();
                            loadedMatchedShape = metadata.getMatchedShape();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading metadata file " + metaFilename, e);
                    }
                }
            } else {
                Log.w(TAG, "Storage directory for metadata is null.");
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
        if (transportMode == null) return Color.DKGRAY; // Default dark gray
        switch (transportMode) {
            case "Walking":    return Color.parseColor("#4CAF50"); // Material Green 500
            case "Bicycling":  return Color.parseColor("#2196F3"); // Material Blue 500
            case "In Vehicle": return Color.parseColor("#F44336"); // Material Red 500
            default:           return Color.parseColor("#757575"); // Material Grey 600
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            Log.d(TAG, "Shutting down backgroundExecutor in onDestroy.");
            backgroundExecutor.shutdown();
        }
    }

    private List<JourneyDetails> processSegmentsIntoJourneys(List<SegmentData> sortedSegments) {
        Log.d(TAG, "processSegmentsIntoJourneys: Processing " + (sortedSegments != null ? sortedSegments.size() : 0) + " segments.");
        List<JourneyDetails> finalProcessedJourneys = new ArrayList<>();
        if (sortedSegments == null || sortedSegments.isEmpty()) {
            return finalProcessedJourneys;
        }

        final long MAX_TIME_GAP_MS = 10 * 60 * 1000; // 10 minutes
        final float MAX_DISTANCE_GAP_METERS = 750.0f; // Adjusted distance gap, was 500

        List<PolylinePoint> currentJourneyPoints = new ArrayList<>();
        List<String> currentJourneyFilenames = new ArrayList<>();
        SegmentData lastSegmentInCurrentJourney = null;

        for (SegmentData currentSegment : sortedSegments) {
            if (currentSegment == null || currentSegment.getPoints() == null || currentSegment.getPoints().isEmpty()) {
                Log.w(TAG, "Skipping null or empty segment.");
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
                        // Connect if primary modes match OR if one is primary and other is transient
                        if (isPrimaryMode(previousMode) && previousMode.equals(nextMode)) {
                            connect = true;
                        } else if ((isPrimaryMode(previousMode) && isTransientMode(nextMode)) ||
                                (isTransientMode(previousMode) && isPrimaryMode(nextMode))) {
                            // If connecting transient to primary, the primary mode dominates for continuity
                            connect = true;
                        } else if (isTransientMode(previousMode) && isTransientMode(nextMode)){
                            // Connect two transient segments (e.g. Still -> Unknown)
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
        Log.i(TAG,"Finished processing segments. Created " + finalProcessedJourneys.size() + " final journeys.");
        Collections.reverse(finalProcessedJourneys); // Newest first
        Log.i(TAG,"Reversed final journey list for display.");
        return finalProcessedJourneys;
    }
}
