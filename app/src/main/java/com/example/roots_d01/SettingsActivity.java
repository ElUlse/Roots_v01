package com.example.roots_d01;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView; // Keep for Spinner temporarily
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView; // NEW Import
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar; // REMOVE Import if completely replaced
import android.widget.Spinner; // REMOVE Import
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Assuming you might add a Toolbar later
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.radiobutton.MaterialRadioButton; // NEW Import
import com.google.android.material.slider.Slider; // NEW Import
import com.google.android.material.slider.Slider.OnChangeListener; // NEW Import
import com.google.android.material.slider.Slider.OnSliderTouchListener; // NEW Import
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout; // NEW Import
import com.skydoves.colorpickerview.ColorPickerDialog;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    // UI Elements
    private TextView walkingColorView, bicyclingColorView, inVehicleColorView;
    // private Spinner mapTileSourceSpinner; // REMOVED
    private AutoCompleteTextView mapStyleDropdown; // NEW
    private TextInputLayout mapStyleDropdownLayout; // Optional NEW for accessing TextInputLayout
    private Button saveBtn;
    private Button clearCacheBtn;
    // private SeekBar polylineThicknessSeekBar; // REMOVED
    private Slider polylineThicknessSlider; // NEW
    // private SeekBar deletePeriodSeekBar; // REMOVED
    private Slider deletePeriodSlider; // NEW
    private TextView deletePeriodValueTextView;
    private Button executeClearOlderBtn;
    private Button executeClearAllBtn;
    private TextView tracksStorageInfoTextView;
    // private SeekBar initialDistanceSeekBar; // REMOVED
    private Slider initialDistanceSlider; // NEW
    private TextView initialDistanceValueTextView;
    // private SeekBar cacheSizeSeekBar; // REMOVED
    private Slider cacheSizeSlider; // NEW
    private TextView cacheSizeValueTextView;
    private Button btnRematchAllJourneys;
    private RadioGroup historicalVisibilityRadioGroup;
    private SwitchMaterial switchLockOrientation; // Keep as SwitchMaterial
    private TextView currentCacheUsageTextView;


    // Preferences Keys (Public for MainActivity access if needed)
    public static final String KEY_POLYLINE_THICKNESS = "polylineThickness";
    public static final String KEY_WALKING_COLOR = "walkingColor";
    public static final String KEY_BICYCLING_COLOR = "bicyclingColor";
    public static final String KEY_IN_VEHICLE_COLOR = "inVehicleColor";
    public static final String KEY_MAP_STYLE_IDENTIFIER = "mapTileSource"; // Keep old key name for compatibility
    public static final String KEY_DELETE_PERIOD_DAYS = "deletePeriodDays";
    public static final String KEY_INITIAL_RECORDING_DISTANCE_METERS = "initialRecordingDistanceMeters";
    public static final String KEY_MAP_CACHE_SIZE_MB = "mapCacheSizeMB";
    public static final String KEY_HISTORICAL_VISIBILITY_MODE = "historicalVisibilityMode";
    public static final String KEY_LOCK_ORIENTATION = "lockOrientation";

    // Default values
    public static final int DEFAULT_POLYLINE_THICKNESS = 5;
    public static final int DEFAULT_WALKING_COLOR = Color.GREEN;
    public static final int DEFAULT_BICYCLING_COLOR = Color.BLUE;
    public static final int DEFAULT_IN_VEHICLE_COLOR = Color.RED;
    public static final String DEFAULT_MAP_STYLE = "OSM Bright";
    public static final int DEFAULT_DELETE_PERIOD_DAYS = 7; // Default to 7 days
    public static final int DEFAULT_INITIAL_DISTANCE = 50; // Default initial distance meters
    public static final int DEFAULT_MAP_CACHE_SIZE_MB = 500; // Default cache size MB
    public static final int MODE_SHOW_ALL = 0; // Constant for radio button logic
    public static final int MODE_HIDE_ALL = 1;
    public static final int MODE_HIDE_UNRELATED = 2;
    public static final int DEFAULT_HISTORICAL_VISIBILITY_MODE = MODE_SHOW_ALL; // Default show all
    public static final boolean DEFAULT_LOCK_ORIENTATION = true;

    // Temporary storage for color changes
    private int tempWalkingColor;
    private int tempBicyclingColor;
    private int tempInVehicleColor;

    // Background Executor
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private boolean settingsChanged = false; // Flag to track if settings were changed
    private boolean tracksCleared = false; // Flag to indicate tracks were cleared
    private boolean forceRematchOnNextLoad = false; // Flag for force rematch


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false); // Enable edge-to-edge

        setContentView(R.layout.activity_settings);

        // Find Views
        try {
            walkingColorView = findViewById(R.id.walkingColorView);
            bicyclingColorView = findViewById(R.id.bicyclingColorView);
            inVehicleColorView = findViewById(R.id.inVehicleColorView);
            // mapTileSourceSpinner = findViewById(R.id.mapTileSourceSpinner); // REMOVED
            mapStyleDropdown = findViewById(R.id.mapStyleDropdown); // NEW ID
            mapStyleDropdownLayout = findViewById(R.id.mapStyleDropdownLayout); // NEW ID
            saveBtn = findViewById(R.id.saveSettingsButton);
            clearCacheBtn = findViewById(R.id.clearCacheButton);
            // polylineThicknessSeekBar = findViewById(R.id.polylineThicknessSeekBar); // REMOVED
            polylineThicknessSlider = findViewById(R.id.polylineThicknessSeekBar); // NEW, ID kept same
            deletePeriodSlider = findViewById(R.id.deletePeriodSeekBar); // NEW, ID kept same
            deletePeriodValueTextView = findViewById(R.id.deletePeriodValueTextView);
            executeClearOlderBtn = findViewById(R.id.executeClearOlderTracksButton);
            executeClearAllBtn = findViewById(R.id.executeClearAllTracksButton);
            tracksStorageInfoTextView = findViewById(R.id.tracksStorageInfoTextView);
            // initialDistanceSeekBar = findViewById(R.id.initialDistanceSeekBar); // REMOVED
            initialDistanceSlider = findViewById(R.id.initialDistanceSeekBar); // NEW, ID kept same
            initialDistanceValueTextView = findViewById(R.id.initialDistanceValueTextView);
            // cacheSizeSeekBar = findViewById(R.id.cacheSizeSeekBar); // REMOVED
            cacheSizeSlider = findViewById(R.id.cacheSizeSeekBar); // NEW, ID kept same
            cacheSizeValueTextView = findViewById(R.id.cacheSizeValueTextView);
            btnRematchAllJourneys = findViewById(R.id.btnRematchAllJourneys);
            historicalVisibilityRadioGroup = findViewById(R.id.historicalVisibilityRadioGroup);
            switchLockOrientation = findViewById(R.id.switchLockOrientation);
            currentCacheUsageTextView = findViewById(R.id.currentCacheUsageTextView);

            // Null check critical views
            if (mapStyleDropdown == null || polylineThicknessSlider == null || saveBtn == null /* add checks for others */) {
                throw new NullPointerException("One or more required settings views not found...");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error finding views in SettingsActivity", e);
            Toast.makeText(this, "Error initializing settings screen.", Toast.LENGTH_LONG).show();
            finish(); // Close activity if views are missing
            return;
        }

        // --- Apply Insets for Edge-to-Edge ---
        View rootView = findViewById(R.id.settingsScrollView); // Assuming ScrollView is the root content container
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                // Apply padding to the ScrollView's direct child (the LinearLayout)
                View content = ((ViewGroup) v).getChildAt(0);
                if (content != null) {
                    content.setPadding(systemBarInsets.left + content.getPaddingLeft(), // Keep original horizontal padding
                            systemBarInsets.top + content.getPaddingTop(),    // Add top inset to original padding
                            systemBarInsets.right + content.getPaddingRight(),// Keep original horizontal padding
                            systemBarInsets.bottom + content.getPaddingBottom()); // Add bottom inset
                    Log.d(TAG, "Applied system bar insets as padding to content. Bottom: " + systemBarInsets.bottom);
                }
                // Don't consume insets for the ScrollView itself, let the child handle it
                return windowInsets;
            });
        } else {
            Log.e(TAG, "Root layout (settingsScrollView) not found!");
        }

        // Load initial values
        loadSettings();
        updateStorageInfo(); // Calculate and display storage info
        updateCurrentCacheUsageDisplay(); // Display N/A for current usage

        // Setup Listeners
        setupListeners();
    }

    private void setupListeners() {
        // Color Picker Listeners
        walkingColorView.setOnClickListener(v -> showColorPickerDialog("Walking", tempWalkingColor));
        bicyclingColorView.setOnClickListener(v -> showColorPickerDialog("Bicycling", tempBicyclingColor));
        inVehicleColorView.setOnClickListener(v -> showColorPickerDialog("In Vehicle", tempInVehicleColor));

        // --- Slider Listeners (NEW) ---
        if (polylineThicknessSlider != null) {
            polylineThicknessSlider.addOnChangeListener((slider, value, fromUser) -> {
                // Optional: Update a TextView showing the thickness value if you add one
                // thicknessValueTextView.setText(String.format(Locale.US, "%.0f", value));
                settingsChanged = true; // Mark settings as changed
            });
        }

        if (initialDistanceSlider != null && initialDistanceValueTextView != null) {
            initialDistanceSlider.addOnChangeListener((slider, value, fromUser) -> {
                initialDistanceValueTextView.setText(String.format(Locale.getDefault(), "%d m", (int) value));
                settingsChanged = true;
            });
        }

        if (deletePeriodSlider != null && deletePeriodValueTextView != null) {
            deletePeriodSlider.addOnChangeListener((slider, value, fromUser) -> {
                int days = Math.round(value); // Round to nearest int for display
                if (days == 0) {
                    deletePeriodValueTextView.setText("Disabled");
                } else if (days == 1) {
                    deletePeriodValueTextView.setText("1 day");
                } else {
                    deletePeriodValueTextView.setText(String.format(Locale.getDefault(), "%d days", days));
                }
                settingsChanged = true;
            });
        }

        if (cacheSizeSlider != null && cacheSizeValueTextView != null) {
            cacheSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
                cacheSizeValueTextView.setText(String.format(Locale.getDefault(), "%d MB", (int)value));
                settingsChanged = true;
            });
        }
        // --- End Slider Listeners ---

        // --- Exposed Dropdown Menu Listener (NEW) ---
        if (mapStyleDropdown != null) {
            // Retrieve style names - ensure this list matches the order in MapManager.PREDEFINED_STYLE_URLS if used
            List<String> styleNames = new ArrayList<>(MapManager.PREDEFINED_STYLE_URLS.keySet());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, styleNames);
            mapStyleDropdown.setAdapter(adapter);

            mapStyleDropdown.setOnItemClickListener((parent, view, position, id) -> {
                String selectedStyleName = (String) parent.getItemAtPosition(position);
                Log.d(TAG, "Map Style selected: " + selectedStyleName);
                settingsChanged = true;
                // No need to save immediately, save button handles it
            });
        }
        // --- End Exposed Dropdown Menu Listener ---

        // --- RadioGroup Listener (No change needed) ---
        if (historicalVisibilityRadioGroup != null) {
            historicalVisibilityRadioGroup.setOnCheckedChangeListener((group, checkedId) -> settingsChanged = true);
        }

        // --- Switch Listener (No change needed) ---
        if (switchLockOrientation != null) {
            switchLockOrientation.setOnCheckedChangeListener((buttonView, isChecked) -> settingsChanged = true);
        }

        // --- Button Listeners (No change needed for MaterialButton) ---
        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                saveSettings();
                Toast.makeText(SettingsActivity.this, "Settings Saved", Toast.LENGTH_SHORT).show();
                setResult(Activity.RESULT_OK, createResultIntent()); // Pass back flags
                finish(); // Close activity after saving
            });
        }

        if (clearCacheBtn != null) {
            clearCacheBtn.setOnClickListener(v -> {
                Log.d(TAG, "Clear Cache button clicked.");
                new AlertDialog.Builder(this)
                        .setTitle("Confirm Clear Cache")
                        .setMessage("Are you sure you want to clear the map tile cache?")
                        .setPositiveButton("Clear Cache", (dialog, which) -> {
                            backgroundExecutor.execute(() -> {
                                try {
                                    org.maplibre.android.offline.OfflineManager offlineManager = org.maplibre.android.offline.OfflineManager.getInstance(getApplicationContext());
                                    offlineManager.clearAmbientCache(new org.maplibre.android.offline.OfflineManager.FileSourceCallback() {
                                        @Override
                                        public void onSuccess() {
                                            mainThreadHandler.post(() -> {
                                                Toast.makeText(SettingsActivity.this, "Map cache cleared.", Toast.LENGTH_SHORT).show();
                                                updateCurrentCacheUsageDisplay(); // Update display
                                            });
                                            Log.i(TAG, "Map cache cleared successfully.");
                                        }
                                        @Override
                                        public void onError(@NonNull String message) {
                                            mainThreadHandler.post(() -> Toast.makeText(SettingsActivity.this, "Error clearing cache: " + message, Toast.LENGTH_LONG).show());
                                            Log.e(TAG, "Error clearing map cache: " + message);
                                        }
                                    });

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
        }

        if (executeClearOlderBtn != null) {
            executeClearOlderBtn.setOnClickListener(v -> {
                int days = Math.round(deletePeriodSlider.getValue()); // Use Slider value
                if (days > 0) {
                    showDeleteConfirmationDialog(days); // Pass days to delete
                } else {
                    Toast.makeText(this, "Deletion period must be set to delete older tracks.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (executeClearAllBtn != null) {
            executeClearAllBtn.setOnClickListener(v -> showDeleteConfirmationDialog(0)); // Pass 0 to signify delete all
        }

        if (btnRematchAllJourneys != null) {
            btnRematchAllJourneys.setOnClickListener(v -> {
                forceRematchOnNextLoad = true; // Set the flag
                settingsChanged = true; // Indicate a change occurred
                Toast.makeText(this, "All journeys will be re-matched on next load.", Toast.LENGTH_SHORT).show();
                Log.i(TAG,"Force Rematch flag set to true.");
                // No need to save immediately, save button handles it or onPause/onDestroy
            });
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        tempWalkingColor = prefs.getInt(KEY_WALKING_COLOR, DEFAULT_WALKING_COLOR);
        tempBicyclingColor = prefs.getInt(KEY_BICYCLING_COLOR, DEFAULT_BICYCLING_COLOR);
        tempInVehicleColor = prefs.getInt(KEY_IN_VEHICLE_COLOR, DEFAULT_IN_VEHICLE_COLOR);

        walkingColorView.setBackgroundColor(tempWalkingColor);
        bicyclingColorView.setBackgroundColor(tempBicyclingColor);
        inVehicleColorView.setBackgroundColor(tempInVehicleColor);

        int thickness = prefs.getInt(KEY_POLYLINE_THICKNESS, DEFAULT_POLYLINE_THICKNESS);
        if (polylineThicknessSlider != null) polylineThicknessSlider.setValue((float)thickness); // Use setValue with float

        // --- Update Dropdown selection ---
        String savedStyleIdentifier = prefs.getString(KEY_MAP_STYLE_IDENTIFIER, DEFAULT_MAP_STYLE);
        if (mapStyleDropdown != null) {
            mapStyleDropdown.setText(savedStyleIdentifier, false); // Set text without filtering
            Log.d(TAG, "Loaded map style identifier: " + savedStyleIdentifier);
        }
        // --- End Dropdown update ---


        int deleteDays = prefs.getInt(KEY_DELETE_PERIOD_DAYS, DEFAULT_DELETE_PERIOD_DAYS);
        if(deletePeriodSlider != null) deletePeriodSlider.setValue((float)deleteDays); // Use setValue with float
        // Update label immediately after setting slider value
        if (deletePeriodValueTextView != null) {
            if (deleteDays == 0) deletePeriodValueTextView.setText("Disabled");
            else if (deleteDays == 1) deletePeriodValueTextView.setText("1 day");
            else deletePeriodValueTextView.setText(String.format(Locale.getDefault(), "%d days", deleteDays));
        }

        int initialDist = prefs.getInt(KEY_INITIAL_RECORDING_DISTANCE_METERS, DEFAULT_INITIAL_DISTANCE);
        if (initialDistanceSlider != null) initialDistanceSlider.setValue((float)initialDist); // Use setValue with float
        if (initialDistanceValueTextView != null) initialDistanceValueTextView.setText(String.format(Locale.getDefault(), "%d m", initialDist));

        int cacheSizeMB = prefs.getInt(KEY_MAP_CACHE_SIZE_MB, DEFAULT_MAP_CACHE_SIZE_MB);
        if (cacheSizeSlider != null) cacheSizeSlider.setValue((float)cacheSizeMB); // Use setValue with float
        if (cacheSizeValueTextView != null) cacheSizeValueTextView.setText(String.format(Locale.getDefault(), "%d MB", cacheSizeMB));


        // Load Historical Visibility Setting
        int visibilityMode = prefs.getInt(KEY_HISTORICAL_VISIBILITY_MODE, DEFAULT_HISTORICAL_VISIBILITY_MODE);
        if (historicalVisibilityRadioGroup != null) {
            if (visibilityMode == MODE_HIDE_ALL) {
                historicalVisibilityRadioGroup.check(R.id.radioHideAllHistorical);
            } else if (visibilityMode == MODE_HIDE_UNRELATED) {
                historicalVisibilityRadioGroup.check(R.id.radioHideUnrelatedHistorical);
            } else { // Default to SHOW_ALL
                historicalVisibilityRadioGroup.check(R.id.radioShowAllHistorical);
            }
        }

        // Load Orientation Lock Setting
        boolean lockOrientation = prefs.getBoolean(KEY_LOCK_ORIENTATION, DEFAULT_LOCK_ORIENTATION);
        if (switchLockOrientation != null) {
            switchLockOrientation.setChecked(lockOrientation);
        }

        settingsChanged = false; // Reset flag after loading
        forceRematchOnNextLoad = false; // Reset rematch flag
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt(KEY_WALKING_COLOR, tempWalkingColor);
        editor.putInt(KEY_BICYCLING_COLOR, tempBicyclingColor);
        editor.putInt(KEY_IN_VEHICLE_COLOR, tempInVehicleColor);

        // --- Save Slider values ---
        if (polylineThicknessSlider != null) editor.putInt(KEY_POLYLINE_THICKNESS, (int) polylineThicknessSlider.getValue());
        if (deletePeriodSlider != null) editor.putInt(KEY_DELETE_PERIOD_DAYS, Math.round(deletePeriodSlider.getValue())); // Round to int
        if (initialDistanceSlider != null) editor.putInt(KEY_INITIAL_RECORDING_DISTANCE_METERS, (int) initialDistanceSlider.getValue());
        if (cacheSizeSlider != null) editor.putInt(KEY_MAP_CACHE_SIZE_MB, (int) cacheSizeSlider.getValue());
        // --- End Save Slider values ---

        // --- Save Dropdown selection ---
        if (mapStyleDropdown != null) {
            String selectedStyleName = mapStyleDropdown.getText().toString();
            // Validate? Could check against the adapter's items if needed.
            editor.putString(KEY_MAP_STYLE_IDENTIFIER, selectedStyleName);
            Log.d(TAG, "Saving map style identifier: " + selectedStyleName);
        }
        // --- End Save Dropdown selection ---


        // Save Historical Visibility Setting
        if (historicalVisibilityRadioGroup != null) {
            int checkedId = historicalVisibilityRadioGroup.getCheckedRadioButtonId();
            int modeToSave = DEFAULT_HISTORICAL_VISIBILITY_MODE; // Default
            if (checkedId == R.id.radioHideAllHistorical) {
                modeToSave = MODE_HIDE_ALL;
            } else if (checkedId == R.id.radioHideUnrelatedHistorical) {
                modeToSave = MODE_HIDE_UNRELATED;
            } // else it remains MODE_SHOW_ALL
            editor.putInt(KEY_HISTORICAL_VISIBILITY_MODE, modeToSave);
            Log.d(TAG, "Saving historical visibility mode: " + modeToSave);
        }

        // Save Orientation Lock Setting
        if (switchLockOrientation != null) {
            editor.putBoolean(KEY_LOCK_ORIENTATION, switchLockOrientation.isChecked());
            Log.d(TAG, "Saving orientation lock: " + switchLockOrientation.isChecked());
        }

        editor.apply(); // Apply changes
        settingsChanged = false; // Reset flag after saving

        // Apply cache size immediately
        if (cacheSizeSlider != null) {
            applyMapLibreCacheSize((int) cacheSizeSlider.getValue());
        }
    }

    private void applyMapLibreCacheSize(int sizeMB) {
        long cacheSizeBytes = (long) sizeMB * 1024 * 1024;
        org.maplibre.android.offline.OfflineManager offlineManager = org.maplibre.android.offline.OfflineManager.getInstance(this);
        offlineManager.setMaximumAmbientCacheSize(cacheSizeBytes, new org.maplibre.android.offline.OfflineManager.FileSourceCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Successfully applied MapLibre ambient cache size to " + sizeMB + " MB");
            }
            @Override
            public void onError(@NonNull String message) {
                Log.e(TAG, "Error applying MapLibre ambient cache size: " + message);
            }
        });
    }


    private void showColorPickerDialog(String mode, int initialColor) {
        new ColorPickerDialog.Builder(this)
                .setTitle(mode + " Color")
                .setPreferenceName("ColorPickerDialog_" + mode) // Unique preference name
                .setPositiveButton(getString(android.R.string.ok),
                        (ColorEnvelopeListener) (envelope, fromUser) -> {
                            int selectedColor = envelope.getColor();
                            // Temporarily store the color
                            switch (mode) {
                                case "Walking": tempWalkingColor = selectedColor; break;
                                case "Bicycling": tempBicyclingColor = selectedColor; break;
                                case "In Vehicle": tempInVehicleColor = selectedColor; break;
                            }
                            // Update the preview view
                            updateColorPreview(mode, selectedColor);
                            settingsChanged = true; // Mark settings as changed
                        })
                .setNegativeButton(getString(android.R.string.cancel),
                        (dialogInterface, i) -> dialogInterface.dismiss())
                .attachAlphaSlideBar(false) // No alpha needed for polylines
                .show();
    }

    private void updateColorPreview(String mode, int color) {
        switch (mode) {
            case "Walking": walkingColorView.setBackgroundColor(color); break;
            case "Bicycling": bicyclingColorView.setBackgroundColor(color); break;
            case "In Vehicle": inVehicleColorView.setBackgroundColor(color); break;
        }
    }


    private void updateStorageInfo() {
        backgroundExecutor.execute(() -> {
            File directory = getExternalFilesDir(null);
            long totalSize = 0;
            if (directory != null) {
                File[] files = directory.listFiles((dir, name) -> name.startsWith("polyline_data_") || name.startsWith("journey_meta_"));
                if (files != null) {
                    for (File file : files) {
                        totalSize += file.length();
                    }
                }
            }
            final String formattedSize = Formatter.formatFileSize(this, totalSize);
            // Calculate percentage (optional, needs total device storage which is tricky)
            // long totalDeviceSpace = directory.getTotalSpace(); // Total internal storage
            // String percentageText = "";
            // if (totalDeviceSpace > 0) {
            //     double percentage = (double) totalSize * 100.0 / totalDeviceSpace;
            //     percentageText = String.format(Locale.getDefault(), " (%.1f%% of internal)", percentage);
            // }
            mainThreadHandler.post(() -> {
                if (tracksStorageInfoTextView != null) {
                    tracksStorageInfoTextView.setText("Saved Tracks Size: " + formattedSize);
                }
                // if (tracksStoragePercentageTextView != null) {
                //     tracksStoragePercentageTextView.setText("Track Storage: " + percentageText);
                // }
            });
        });
    }

    private void showDeleteConfirmationDialog(int days) {
        String message;
        String title;
        if (days == 0) {
            title = "Delete ALL Journeys?";
            message = "Are you sure you want to permanently delete ALL saved journey data?\nThis action cannot be undone.";
        } else {
            title = "Delete Old Journeys?";
            message = String.format(Locale.getDefault(),
                    "Are you sure you want to permanently delete all saved journey data older than %d days?\nThis action cannot be undone.", days);
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) -> deleteTracks(days))
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteTracks(int daysOlderThan) {
        backgroundExecutor.execute(() -> {
            File directory = getExternalFilesDir(null);
            long cutoffTime = 0;
            if (daysOlderThan > 0) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, -daysOlderThan);
                cutoffTime = cal.getTimeInMillis();
            }

            int deleteCount = 0;
            if (directory != null) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        boolean shouldDelete = false;
                        if (file.getName().startsWith("polyline_data_") || file.getName().startsWith("journey_meta_")) {
                            if (daysOlderThan == 0) { // Delete all condition
                                shouldDelete = true;
                            } else { // Delete older than condition
                                try {
                                    // Extract timestamp from filename
                                    String namePart = file.getName().startsWith("polyline_data_") ? "polyline_data_" : "journey_meta_";
                                    String timestampStr = file.getName().substring(namePart.length()).replace(".json", "");
                                    long fileTimestamp = Long.parseLong(timestampStr);
                                    if (fileTimestamp < cutoffTime) {
                                        shouldDelete = true;
                                    }
                                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                                    Log.e(TAG, "Could not parse timestamp from filename: " + file.getName(), e);
                                }
                            }
                        }

                        if (shouldDelete) {
                            if (file.delete()) {
                                deleteCount++;
                                Log.d(TAG, "Deleted file: " + file.getName());
                            } else {
                                Log.w(TAG, "Failed to delete file: " + file.getName());
                            }
                        }
                    }
                }
            } else {
                mainThreadHandler.post(() -> Toast.makeText(SettingsActivity.this, "Error accessing storage", Toast.LENGTH_SHORT).show());
                return;
            }

            final int finalDeleteCount = deleteCount;
            mainThreadHandler.post(() -> {
                Toast.makeText(SettingsActivity.this, "Deleted " + finalDeleteCount + " files.", Toast.LENGTH_SHORT).show();
                updateStorageInfo(); // Refresh storage info display
                tracksCleared = true; // Set flag to indicate tracks were cleared
                settingsChanged = true; // Also mark settings as changed so result is set
            });
        });
    }

    // Method to create the result intent with flags
    private Intent createResultIntent() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("tracksCleared", tracksCleared);
        resultIntent.putExtra("force_rematch_on_next_load", forceRematchOnNextLoad); // Pass rematch flag
        return resultIntent;
    }

    // Ensure onPause or onBackPressed saves settings if changed
    @Override
    protected void onPause() {
        super.onPause();
        if (settingsChanged) {
            Log.d(TAG, "Settings changed, saving on pause...");
            saveSettings();
            // Set result here too in case user leaves via back press
            setResult(Activity.RESULT_OK, createResultIntent());
        } else {
            // If settings weren't changed directly, but tracks might have been cleared,
            // still set the result so MainActivity knows.
            if (tracksCleared || forceRematchOnNextLoad) {
                setResult(Activity.RESULT_OK, createResultIntent());
            } else {
                setResult(Activity.RESULT_CANCELED);
            }
        }
    }
    private void updateCurrentCacheUsageDisplay() {
        if (currentCacheUsageTextView != null) {
            currentCacheUsageTextView.setText("Current Usage: N/A");
            Log.d(TAG, "Setting current cache usage display to N/A.");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items (if you add an ActionBar/Toolbar)
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // Treat up navigation as back press
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Before finishing, check if settings changed and prompt if necessary,
        // or rely on onPause to save. For simplicity, let onPause handle saving.
        super.onBackPressed(); // This will call onPause eventually
        Log.d(TAG,"Back pressed, settings save/result handled by onPause.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
    }
}