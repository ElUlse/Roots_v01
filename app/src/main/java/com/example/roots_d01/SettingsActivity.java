package com.example.roots_d01;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DecimalFormat;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.core.graphics.Insets;

import android.view.LayoutInflater;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.skydoves.colorpickerview.ColorPickerView;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;
import com.skydoves.colorpickerview.flag.BubbleFlag;
import com.skydoves.colorpickerview.flag.FlagMode;


public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    // --- UI Elements ---
    private SeekBar polylineThicknessSeekBar;
    private TextView walkingColorView;
    private TextView bicyclingColorView;
    private TextView inVehicleColorView;
    private Spinner mapTileSourceSpinner;
    private TextView tracksStorageInfoTextView;
    private SeekBar deletePeriodSeekBar; // For selecting delete period
    private TextView deletePeriodValueTextView; // Displays selected period
    private Button executeClearOlderBtn; // Button to trigger delete older
    // Note: Other buttons like Save, ClearAll, ClearCache are found directly in onCreate

    // --- Settings values ---
    private int selectedWalkingColor = Color.GREEN;
    private int selectedBicyclingColor = Color.BLUE;
    private int selectedInVehicleColor = Color.RED;
    private boolean dataWasCleared = false;
    private SeekBar initialDistanceSeekBar; // NEW
    private TextView initialDistanceValueTextView; // NEW
    // --- Constants for Settings ---
    public static final String KEY_INITIAL_DISTANCE_THRESHOLD = "initialDistanceThresholdMetres"; // NEW Key
    public static final int DEFAULT_INITIAL_DISTANCE_THRESHOLD = 50; // NEW Default (in metres)
    // Note: Max value derived from SeekBar's android:max
    private RadioGroup historicalVisibilityRadioGroup; // <-- Add this
    public static final String KEY_HISTORICAL_VISIBILITY_MODE = "historicalVisibilityMode";
    public static final int MODE_SHOW_ALL = 0;
    public static final int MODE_HIDE_ALL = 1;
    public static final int MODE_HIDE_UNRELATED = 2;
    public static final int DEFAULT_HISTORICAL_VISIBILITY_MODE = MODE_SHOW_ALL; // Default


    // --- Tile Sources for Spinner ---
    private final List<String> mapStyleIdentifiers = Arrays.asList(
            // Predefined MapLibre Styles (using internal constants if desired, but strings are safer for prefs)
            "OSM Bright",   // Corresponds to a common MapLibre community style or MapTiler Bright
            "Streets",      // Common name for street-focused vector styles
            "Outdoors",     // Common name for terrain/outdoor styles
            "Satellite Streets" // Common name for satellite+labels styles
            // You could also add custom style URLs directly here:
            // "https://your-server.com/your-custom-style.json"
    );

    // Choose a default style identifier that matches one in your list above
    public static final String DEFAULT_MAP_STYLE = "OSM Bright"; // Or another default

    // --- Update Preference Key (Optional but good practice) ---
    public static final String KEY_MAP_STYLE_IDENTIFIER = "mapStyleIdentifier"; // New key name


    // --- Background Task Executor ---
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    // --- Constants for Deletion ---
    private static final long DAY_IN_MS = 24 * 60 * 60 * 1000L;
    private static final String KEY_DELETE_OLDER_THAN_DAYS = "deleteOlderThanDays";
    private static final int DEFAULT_DELETE_OLDER_THAN_DAYS = 7; // Default to 7 days


    public static final String KEY_MAP_CACHE_SIZE_MB = "mapCacheSizeMB";
    public static final int DEFAULT_MAP_CACHE_SIZE_MB = 500; // Default 500 MB
    private TextView tracksStoragePercentageTextView;
    private Button btnRematchAllJourneys;
    public static final String KEY_LOCK_ORIENTATION = "lockMapOrientation"; // <<< ADD KEY
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Handler mainThreadHandler;

    // --- onCreate ---
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_settings); // Ensure this layout XML is correct

        prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        editor = prefs.edit();

        Log.d(TAG, "onCreate: Initializing views...");
        // --- Initialize Views ---
        try {
            // Find all views
            polylineThicknessSeekBar = findViewById(R.id.polylineThicknessSeekBar);
            walkingColorView = findViewById(R.id.walkingColorView);
            bicyclingColorView = findViewById(R.id.bicyclingColorView);
            inVehicleColorView = findViewById(R.id.inVehicleColorView);
            mapTileSourceSpinner = findViewById(R.id.mapTileSourceSpinner);
            tracksStorageInfoTextView = findViewById(R.id.tracksStorageInfoTextView); // Use correct ID
            deletePeriodSeekBar = findViewById(R.id.deletePeriodSeekBar);
            deletePeriodValueTextView = findViewById(R.id.deletePeriodValueTextView);
            Button saveBtn = findViewById(R.id.saveSettingsButton);
            executeClearOlderBtn = findViewById(R.id.executeClearOlderTracksButton);
            Button executeClearAllBtn = findViewById(R.id.executeClearAllTracksButton);
            Button clearCacheBtn = findViewById(R.id.clearCacheButton);
            initialDistanceSeekBar = findViewById(R.id.initialDistanceSeekBar); // NEW
            initialDistanceValueTextView = findViewById(R.id.initialDistanceValueTextView); // NEW
            btnRematchAllJourneys = findViewById(R.id.btnRematchAllJourneys);
            SwitchMaterial switchLockOrientation = findViewById(R.id.switchLockOrientation);

            historicalVisibilityRadioGroup = findViewById(R.id.historicalVisibilityRadioGroup); // <-- Add this
            if (historicalVisibilityRadioGroup == null) {
                throw new NullPointerException("historicalVisibilityRadioGroup not found!");
            }

            if (switchLockOrientation != null) {
                // Load current setting (default to true = locked)
                boolean isLocked = prefs.getBoolean(KEY_LOCK_ORIENTATION, true);
                switchLockOrientation.setChecked(isLocked); // Set initial state

                // Save setting when switch changes
                switchLockOrientation.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Log.d("Settings", "Lock Orientation switched. New state (isChecked = Locked): " + isChecked);
                    editor.putBoolean(KEY_LOCK_ORIENTATION, isChecked);
                    // Apply immediately in case user navigates back without explicit save button
                    editor.apply();
                    // Optionally show a toast or visual feedback
                });
            } else {
                Log.e("Settings", "switchLockOrientation not found in layout!");
            }

            View rootScrollView = findViewById(R.id.settingsScrollView); // Get root ScrollView by ID
            if (rootScrollView != null) {
                ViewCompat.setOnApplyWindowInsetsListener(rootScrollView, (v, windowInsets) -> {
                    Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    // Apply padding to the ScrollView itself
                    v.setPadding(
                            systemBarInsets.left,
                            systemBarInsets.top,
                            systemBarInsets.right,
                            systemBarInsets.bottom // This pushes content up from under nav bar
                    );
                    Log.d(TAG, "Applied system bar insets as padding to ScrollView. Bottom: " + systemBarInsets.bottom);
                    // Propagate insets to children if needed (usually padding is enough)
                    return windowInsets;
                });
            } else {
                Log.e(TAG, "Root ScrollView (settingsScrollView) not found!");
            }

            tracksStoragePercentageTextView = findViewById(R.id.tracksStoragePercentageTextView);
            if (tracksStoragePercentageTextView == null) {
                throw new NullPointerException("tracksStoragePercentageTextView not found!");
            }



            // Check if any view is null (indicates layout/ID issue)
            if (walkingColorView == null || bicyclingColorView == null ||
                    inVehicleColorView == null || mapTileSourceSpinner == null || saveBtn == null ||
                    clearCacheBtn == null || deletePeriodSeekBar == null || deletePeriodValueTextView == null ||
                    executeClearOlderBtn == null || executeClearAllBtn == null ||  tracksStorageInfoTextView == null
                ||  initialDistanceSeekBar == null || initialDistanceValueTextView == null || btnRematchAllJourneys == null) {
                throw new NullPointerException("One or more required settings views not found...");
            }



            // --- Setup Spinner Adapter (BEFORE loadSettings) ---
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mapStyleIdentifiers);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mapTileSourceSpinner.setAdapter(adapter); // Use the existing Spinner variable name

            // --- Load Settings (AFTER adapter is set & views found) ---
            loadSettings();

            // --- Set Initial UI State ---
            walkingColorView.setBackgroundColor(selectedWalkingColor);
            bicyclingColorView.setBackgroundColor(selectedBicyclingColor);
            inVehicleColorView.setBackgroundColor(selectedInVehicleColor);
            updateDeletePeriodText(deletePeriodSeekBar.getProgress()); // Set initial text
            executeClearOlderBtn.setEnabled(deletePeriodSeekBar.getProgress() > 0); // Set initial enabled state

            // --- Set Listeners ---
            setupListeners(saveBtn, executeClearAllBtn, clearCacheBtn, btnRematchAllJourneys);

            Log.d(TAG, "onCreate setup complete.");

        } catch (Exception e) {
            Log.e(TAG, "FATAL Error during onCreate setup. Check layout or view init.", e);
            Toast.makeText(this, "Error loading settings screen.", Toast.LENGTH_LONG).show();
            finish(); // Exit if critical setup fails
        }
    }

    // Helper method to setup listeners (called from try block in onCreate)
    private void setupListeners(Button saveBtn, Button executeClearAllBtn, Button clearCacheBtn, Button rematchBtn) {

        // Inside setupListeners method
        if (historicalVisibilityRadioGroup != null) {
            historicalVisibilityRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                Log.d(TAG, "Historical visibility selection changed, saving settings...");
                saveSettings(); // Save whenever selection changes
            });
        }

        // Listener for polylineThicknessSeekBar (Corrected)
        polylineThicknessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Correctly empty now (or add thickness update logic later)
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { saveSettings(); }
        });

        if (mapTileSourceSpinner != null) {
            mapTileSourceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    // Save settings whenever a new item is selected
                    Log.d(TAG, "Spinner item selected, saving settings...");
                    saveSettings();
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    // Do nothing
                }
            });
        } else {
            Log.e(TAG, "setupListeners: mapTileSourceSpinner is null, cannot set listener!");
        }

        // Color Picker Listeners
        walkingColorView.setOnClickListener(v -> showColorPickerDialog(
                "Select Walking Color",
                selectedWalkingColor,
                newColor -> {
                    selectedWalkingColor = newColor;
                    walkingColorView.setBackgroundColor(newColor);
                    saveSettings();
                }
        ));

        bicyclingColorView.setOnClickListener(v -> showColorPickerDialog(
                "Select Bicycling Color",
                selectedBicyclingColor,
                newColor -> {
                    selectedBicyclingColor = newColor;
                    bicyclingColorView.setBackgroundColor(newColor);
                    saveSettings();
                }
        ));

        inVehicleColorView.setOnClickListener(v -> showColorPickerDialog(
                "Select In Vehicle Color",
                selectedInVehicleColor,
                newColor -> {
                    selectedInVehicleColor = newColor;
                    inVehicleColorView.setBackgroundColor(newColor);
                    saveSettings();
                }
        ));

        if (rematchBtn != null) {
            rematchBtn.setOnClickListener(v -> showRematchConfirmationDialog());
        }

        // Save Button Listener
        saveBtn.setOnClickListener(v -> { /* ... */ });

        // Save Button Listener
        saveBtn.setOnClickListener(v -> {
            saveSettings(); // Your existing save method
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            prepareAndFinish(); // Call helper to set result and finish
        });

        // Delete Period SeekBar Listener
        deletePeriodSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDeletePeriodText(progress); // Update text display
                executeClearOlderBtn.setEnabled(progress > 0); // Enable button only if days > 0
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { saveSettings(); } // Save selected days value
        });


        if (initialDistanceSeekBar != null) {
            initialDistanceSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    // Updates the text view in real-time
                    updateInitialDistanceText(progress);
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                    // Save when the user finishes sliding
                    saveSettings();
                }
            });
        } else {
            Log.e(TAG, "setupListeners: initialDistanceSeekBar is null!");
        }

        // Listener for "Delete Older Tracks" Button
        executeClearOlderBtn.setOnClickListener(v -> {
            int days = deletePeriodSeekBar.getProgress();
            if (days <= 0) { Toast.makeText(this, "Slider must be > 0 days", Toast.LENGTH_SHORT).show(); return; }
            showClearOlderConfirmationDialog(days);
        });

        // Listener for "Delete ALL Tracks" Button
        executeClearAllBtn.setOnClickListener(v -> showClearAllConfirmationDialog());

        if (clearCacheBtn != null) {
            clearCacheBtn.setOnClickListener(v -> {
                Log.d(TAG, "Clear Cache button clicked.");
                // Show confirmation dialog
                new AlertDialog.Builder(this)
                        .setTitle("Confirm Clear Cache")
                        .setMessage("Are you sure you want to clear the map tile cache? Tiles will need to be re-downloaded.")
                        .setPositiveButton("Clear Cache", (dialog, which) -> {
                            // *** IMPORTANT: Find the correct MapLibre API call ***
                            // Perform the cache clearing on a background thread
                            backgroundExecutor.execute(() -> {
                                try {
                                    // You'll need to consult the MapLibre SDK documentation for the exact API call.
                                    // It might look something like this (EXAMPLE ONLY - API may differ):
                                    // org.maplibre.android.storage.FileSource.getInstance(getApplicationContext()).clearCache(new org.maplibre.android.storage.FileSource.Callback() {
                                    //     @Override
                                    //     public void onSuccess() {
                                    //         mainThreadHandler.post(() -> Toast.makeText(SettingsActivity.this, "Map cache cleared.", Toast.LENGTH_SHORT).show());
                                    //         Log.i(TAG, "Map cache cleared successfully.");
                                    //     }
                                    //     @Override
                                    //     public void onError(String message) {
                                    //         mainThreadHandler.post(() -> Toast.makeText(SettingsActivity.this, "Error clearing cache: " + message, Toast.LENGTH_LONG).show());
                                    //         Log.e(TAG, "Error clearing map cache: " + message);
                                    //     }
                                    // });
                                    Log.w(TAG, "MapLibre cache clearing API call needed here.");
                                    mainThreadHandler.post(() -> Toast.makeText(SettingsActivity.this, "Cache clearing not implemented yet.", Toast.LENGTH_LONG).show()); // Placeholder

                                } catch (Exception e) {
                                    Log.e(TAG, "Error triggering map cache clear", e);
                                    mainThreadHandler.post(() -> Toast.makeText(SettingsActivity.this, "Error clearing cache.", Toast.LENGTH_SHORT).show());
                                }
                            });
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            });
        } else {
            Log.e(TAG, "setupListeners: clearCacheButton is null!");
        }

    }

    private void showRematchConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Re-match")
                .setMessage("Re-run map matching for ALL saved journeys?\n\nThis will re-process raw GPS data and may take time, consume network data, and potentially alter previously matched routes. Existing matched status will be ignored.")
                .setPositiveButton("Re-match All", (dialog, which) -> {
                    // Set the flag in SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
                    prefs.edit().putBoolean("force_rematch_on_next_load", true).apply();
                    Log.i(TAG, "Force rematch flag set in SharedPreferences.");
                    Toast.makeText(SettingsActivity.this, "Map matching will re-run next time journeys are displayed.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }


    // Helper to update the TextView next to the SeekBar
    private void updateDeletePeriodText(int days) {
        if (deletePeriodValueTextView != null) {
            if (days == 0) { deletePeriodValueTextView.setText("Keep All"); }
            else if (days == 1) { deletePeriodValueTextView.setText("1 day"); }
            else { deletePeriodValueTextView.setText(days + " days"); }
        }
    }

    private void updateInitialDistanceText(int distanceMetres) {
        if (initialDistanceValueTextView != null) {
            if (distanceMetres == 0) {
                initialDistanceValueTextView.setText("Disabled (0 m)");
            } else {
                initialDistanceValueTextView.setText(distanceMetres + " m");
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Refresh storage info and slider text when the user enters the screen
        updateStorageInfo();
        if (deletePeriodSeekBar != null && deletePeriodValueTextView != null && executeClearOlderBtn != null) {
            updateDeletePeriodText(deletePeriodSeekBar.getProgress());
            executeClearOlderBtn.setEnabled(deletePeriodSeekBar.getProgress() > 0);
        }
    }


    // Load settings from SharedPreferences
    private void loadSettings() {
        int polylineThickness = prefs.getInt("polylineThickness", 5);
        selectedWalkingColor = prefs.getInt("walkingColor", Color.GREEN);
        selectedBicyclingColor = prefs.getInt("bicyclingColor", Color.BLUE);
        selectedInVehicleColor = prefs.getInt("inVehicleColor", Color.RED);
        String savedMapStyleId = prefs.getString(KEY_MAP_STYLE_IDENTIFIER, DEFAULT_MAP_STYLE);
        int deleteOlderDays = prefs.getInt(KEY_DELETE_OLDER_THAN_DAYS, DEFAULT_DELETE_OLDER_THAN_DAYS);
        int savedCacheSizeMB = prefs.getInt(KEY_MAP_CACHE_SIZE_MB, DEFAULT_MAP_CACHE_SIZE_MB);
        int initialDistance = prefs.getInt(KEY_INITIAL_DISTANCE_THRESHOLD, DEFAULT_INITIAL_DISTANCE_THRESHOLD);
        int historicalMode = prefs.getInt(KEY_HISTORICAL_VISIBILITY_MODE, DEFAULT_HISTORICAL_VISIBILITY_MODE);


        Log.d(TAG, "Loading Settings: ... DeleteDays=" + deleteOlderDays + ", CacheMB=" + savedCacheSizeMB); // Update log
        Log.d(TAG, "Loading Settings: ... InitialDistance=" + initialDistance); // Update log


        // Apply loaded values to UI elements (check for null just in case)
        if (polylineThicknessSeekBar != null) polylineThicknessSeekBar.setProgress(polylineThickness);
        if (deletePeriodSeekBar != null) deletePeriodSeekBar.setProgress(deleteOlderDays);

        if (initialDistanceSeekBar != null) {
            initialDistanceSeekBar.setProgress(initialDistance); // Progress matches metres (0-200)
        }
        updateInitialDistanceText(initialDistance); // Update the text display initially

        if (historicalVisibilityRadioGroup != null) {
            switch (historicalMode) {
                case MODE_HIDE_ALL:
                    historicalVisibilityRadioGroup.check(R.id.radioHideAllHistorical);
                    break;
                case MODE_HIDE_UNRELATED:
                    historicalVisibilityRadioGroup.check(R.id.radioHideUnrelatedHistorical);
                    break;
                case MODE_SHOW_ALL:
                default:
                    historicalVisibilityRadioGroup.check(R.id.radioShowAllHistorical);
                    break;
            }
        }
        Log.d(TAG, "Loading Settings: ... HistoricalMode=" + historicalMode);


        // Apply loaded style to Spinner
        if (mapTileSourceSpinner != null && mapTileSourceSpinner.getAdapter() != null) { // Check adapter too
            @SuppressWarnings("unchecked") ArrayAdapter<String> adapter = (ArrayAdapter<String>) mapTileSourceSpinner.getAdapter();

            // Use the new list variable name if you changed it
            int spinnerPosition = adapter.getPosition(savedMapStyleId);
            if (spinnerPosition >= 0) {
                mapTileSourceSpinner.setSelection(spinnerPosition);
            } else {
                // If saved style not found, select the default one's position
                int defaultPosition = adapter.getPosition(DEFAULT_MAP_STYLE);
                mapTileSourceSpinner.setSelection(Math.max(0, defaultPosition)); // Select default or 0
                Log.w(TAG, "Saved map style '" + savedMapStyleId + "' not found in options. Defaulting.");
            }
        } else { Log.e(TAG, "Spinner/Adapter null in loadSettings!"); }
        Log.d(TAG, "Settings saving initiated."); // Simplified log
    }

    // Add this method inside SettingsActivity.java
    private void prepareAndFinish() {
        Intent resultIntent = new Intent();
        if (dataWasCleared) {
            Log.d(TAG, "Finishing with RESULT_OK and tracksCleared=true");
            resultIntent.putExtra("tracksCleared", true);
            setResult(Activity.RESULT_OK, resultIntent);
        } else {
            Log.d(TAG, "Finishing with RESULT_OK (no tracks cleared or flag not set)");
            // Still send RESULT_OK if settings were saved or no action requiring specific result occurred
            setResult(Activity.RESULT_OK, resultIntent);
        }
        finish(); // Close SettingsActivity
    }
    // Save settings to SharedPreferences
    private void saveSettings() {

        if (deletePeriodSeekBar != null) editor.putInt(KEY_DELETE_OLDER_THAN_DAYS, deletePeriodSeekBar.getProgress());

        if (initialDistanceSeekBar != null) {
            int initialDistance = initialDistanceSeekBar.getProgress(); // Progress matches metres
            editor.putInt(KEY_INITIAL_DISTANCE_THRESHOLD, initialDistance);
            Log.d(TAG, "Saving Initial Distance Threshold: " + initialDistance + " m");
        }

        if(polylineThicknessSeekBar != null) editor.putInt("polylineThickness", polylineThicknessSeekBar.getProgress());
        editor.putInt("walkingColor", selectedWalkingColor);
        editor.putInt("bicyclingColor", selectedBicyclingColor);
        editor.putInt("inVehicleColor", selectedInVehicleColor);

        if (mapTileSourceSpinner != null && mapTileSourceSpinner.getSelectedItem() != null) {
            String selectedStyleIdentifier = mapTileSourceSpinner.getSelectedItem().toString();
            editor.putString(KEY_MAP_STYLE_IDENTIFIER, selectedStyleIdentifier); // Use the constant key
            Log.d(TAG, "Saving Map Style Identifier: " + selectedStyleIdentifier);
        } else {
            Log.w(TAG, "saveSettings: Spinner or selected item is null, cannot save map style.");
        }

        if (deletePeriodSeekBar != null) {
            editor.putInt(KEY_DELETE_OLDER_THAN_DAYS, deletePeriodSeekBar.getProgress());
            Log.d(TAG, "Saving Delete Older Than Days: " + deletePeriodSeekBar.getProgress());
        }

        if (historicalVisibilityRadioGroup != null) {
            int selectedId = historicalVisibilityRadioGroup.getCheckedRadioButtonId();
            int modeToSave;
            if (selectedId == R.id.radioHideAllHistorical) {
                modeToSave = MODE_HIDE_ALL;
            } else if (selectedId == R.id.radioHideUnrelatedHistorical) {
                modeToSave = MODE_HIDE_UNRELATED;
            } else { // Default to Show All
                modeToSave = MODE_SHOW_ALL;
            }
            editor.putInt(KEY_HISTORICAL_VISIBILITY_MODE, modeToSave);
            Log.d(TAG, "Saving Historical Visibility Mode: " + modeToSave);
        }

        editor.apply(); // Use apply() for asynchronous saving
        Log.d(TAG, "Settings saving initiated.");

    }



    // --- Background Task Methods ---
// Modified background task for clearing tracks
    private void clearSavedTracksInBackground(long cutoffTimestamp) { // Accepts timestamp now
        backgroundExecutor.execute(() -> {
            String criteriaDesc = (cutoffTimestamp == -1) ? "ALL" : "older than " + cutoffTimestamp;
            Log.d(TAG, "Starting BG track deletion: " + criteriaDesc);
            File directory = getExternalFilesDir(null);
            if (directory == null) { Log.e(TAG,"Cannot access files dir."); runOnUiThread(()->Toast.makeText(this,"Error",Toast.LENGTH_SHORT).show()); return; }

            File[] files = directory.listFiles((dir, name) -> name.startsWith("polyline_data_") && name.endsWith(".json"));
            int deletedCount = 0; int keptCount = 0;

            if (files != null) {
                for (File file : files) {
                    if (!file.isFile()) continue;
                    boolean shouldDelete = false;
                    if (cutoffTimestamp == -1) { // -1 signifies delete all
                        shouldDelete = true;
                    } else {
                        try { // Parse timestamp from filename
                            String name = file.getName();
                            String timestampStr = name.substring("polyline_data_".length(), name.length() - ".json".length());
                            long fileTimestamp = Long.parseLong(timestampStr);
                            if (fileTimestamp < cutoffTimestamp) { // Compare
                                shouldDelete = true;
                            } else { keptCount++; }
                        } catch (Exception e) { Log.e(TAG, "Could not parse timestamp: " + file.getName(), e); keptCount++; } // Keep badly named files
                    }
                    if (shouldDelete) {
                        if (file.delete()) { deletedCount++; }
                        else { Log.e(TAG, "Failed delete: " + file.getName()); keptCount++; } // Count failed deletes as kept
                    }
                }
            }
            final int finalDeletedCount = deletedCount; final int finalKeptCount = keptCount;
            Log.d(TAG, "Finished BG track deletion. Deleted: " + finalDeletedCount + ", Kept: " + finalKeptCount);

            runOnUiThread(() -> { // Update UI on main thread
                String toastMessage = "Deleted " + finalDeletedCount + " track(s).";
                if (cutoffTimestamp != -1) toastMessage += " (" + finalKeptCount + " kept)";
                Toast.makeText(SettingsActivity.this, toastMessage, Toast.LENGTH_LONG).show();
                updateStorageInfo(); // Refresh storage info
            });
        });
    }




    // --- onDestroy to shut down executor ---
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
            Log.d(TAG, "Background executor shut down.");
        }
    }

    private long getTotalStorageSize() {
        // Use getFilesDir() to get the internal storage directory for the app
        File internalStorageDir = getFilesDir().getAbsoluteFile().getParentFile();
        long totalSpace = internalStorageDir.getTotalSpace();
        Log.d(TAG, "Total internal storage: " + totalSpace + " bytes");
        return totalSpace;
    }

    private String formatStoragePercentage(long used, long total) {
        if (total == 0) return "0%"; // Avoid division by zero
        float percentage = ((float) used / total) * 100;
        return fileSizeFmt.format(percentage) + "%"; // Use existing formatter
    }
    private void updateStorageInfo() {
        // Set placeholder text while calculating
        if (tracksStorageInfoTextView != null) {
            tracksStorageInfoTextView.setText("Calculating track size...");
        }
        if (tracksStoragePercentageTextView != null) {
            tracksStoragePercentageTextView.setText("Calculating track %...");
        }

        backgroundExecutor.execute(() -> {
            long totalSize = 0;
            File directory = getApplicationContext().getExternalFilesDir(null);
            long totalStorage = getTotalStorageSize(); // Implement this method (see below)
            if (directory != null && directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles((dir, name) -> name.startsWith("polyline_data_") && name.endsWith(".json"));
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            totalSize += file.length();
                        }
                    }
                } else {
                    Log.w(TAG, "listFiles returned null for track directory.");
                }
            } else {
                Log.e(TAG, "Track directory is null or doesn't exist.");
            }

            final String formattedSize = formatFileSize(totalSize);
            final String percentageUsed = formatStoragePercentage(totalSize, totalStorage);
            Log.d(TAG, "Calculated total track size: " + totalSize + " bytes (" + formattedSize + ") - " + percentageUsed + " of total");

            runOnUiThread(() -> {
                if (tracksStorageInfoTextView != null) {
                    tracksStorageInfoTextView.setText("Saved Tracks Size: " + formattedSize);
                }
                if (tracksStoragePercentageTextView != null) {
                    tracksStoragePercentageTextView.setText("Track Storage: " + percentageUsed + " of total");
                }
            });
        });
    }

    // --- Add this helper method to format bytes ---
    private static final DecimalFormat fileSizeFmt = new DecimalFormat("#.##");
    private String formatFileSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        // Ensure digitGroups is within the bounds of the units array
        digitGroups = Math.min(digitGroups, units.length - 1);
        return fileSizeFmt.format(sizeBytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }


    private void showClearOlderConfirmationDialog(int days) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Delete")
                .setMessage("Delete all tracks older than " + days + " day(s)?\nThis cannot be undone.")
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    long cutoffTimestamp = System.currentTimeMillis() - ((long)days * DAY_IN_MS);
                    dataWasCleared = true; // <<< SET FLAG HERE
                    clearSavedTracksInBackground(cutoffTimestamp); // Start background deletion
                })
                .setNegativeButton(android.R.string.no, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    // Dialog specifically for deleting ALL tracks
    private void showClearAllConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Delete ALL")
                .setMessage("DELETE ALL SAVED TRACKS?\nThis cannot be undone.")
                .setPositiveButton("DELETE ALL", (dialog, which) -> { // Emphasize action
                    dataWasCleared = true; // <<< SET FLAG HERE
                    clearSavedTracksInBackground(-1L); // Pass -1 to signal delete all
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }



    // Add this method override inside SettingsActivity.java
    @Override
    public void onBackPressed() {
        // If user manually saved before pressing back, saveSettings() was already called.
        // If they didn't save, saveSettings() could be called here if desired,
        // or just finish with the current state (dataWasCleared flag).
        // Let's just prepare result and finish based on dataWasCleared flag.
        prepareAndFinish();
        // Note: DO NOT call super.onBackPressed() here, as prepareAndFinish calls finish().
    }
    private void showColorPickerDialog(String title, @ColorInt int initialColor, java.util.function.Consumer<Integer> onColorSelected) {
        // Inflate the custom layout
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_color_picker, null);
        final ColorPickerView colorPickerView = dialogView.findViewById(R.id.colorPickerViewDialog);

        // Optional: Set up Alpha and Brightness sliders if you included them in the XML
        // final com.skydoves.colorpickerview.sliders.AlphaSlideBar alphaSlideBar = dialogView.findViewById(R.id.alphaSlideBarDialog);
        // final com.skydoves.colorpickerview.sliders.BrightnessSlideBar brightnessSlideBar = dialogView.findViewById(R.id.brightnessSlideBarDialog);
        // if (alphaSlideBar != null) colorPickerView.attachAlphaSlider(alphaSlideBar);
        // if (brightnessSlideBar != null) colorPickerView.attachBrightnessSlider(brightnessSlideBar);

        // Set an initial color (optional, but good)
        colorPickerView.setInitialColor(initialColor);

        // Add a flag view to show the selected color preview
        BubbleFlag bubbleFlag = new BubbleFlag(this);
        bubbleFlag.setFlagMode(FlagMode.FADE); // Or ALWAYS
        colorPickerView.setFlagView(bubbleFlag);

        // Build the AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setView(dialogView); // Set the custom view

        // Use a variable to store the last selected color within the dialog instance
        final int[] selectedColor = {initialColor};

        // Listener to update the color as the user drags
        colorPickerView.setColorListener((ColorEnvelopeListener) (envelope, fromUser) -> {
            selectedColor[0] = envelope.getColor();
            // You could update something live here if needed, e.g., the dialog button color
        });

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            // Pass the final selected color back via the callback
            onColorSelected.accept(selectedColor[0]);
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        builder.show(); // Show the dialog
    }
} // --- End of SettingsActivity ---