package com.example.roots_d01;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
// import android.location.GnssStatus; // Keep if used in showGPSStatus
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

// --- AndroidX Imports ---
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// --- Google Play Services Imports ---
import com.example.roots_d01.network.ValhallaRequest;
import com.example.roots_d01.network.ValhallaResponse;
import com.example.roots_d01.network.ValhallaService;

// --- MapLibre Imports --- // Updated imports
import org.maplibre.android.MapLibre;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;

import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;
import org.maplibre.geojson.Feature;

// --- Gson Imports ---
import com.google.android.gms.location.DetectedActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

// --- Java IO Imports ---
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.CompoundButton;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.app.Activity;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;


import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;

import android.graphics.PointF;

public class MainActivity extends AppCompatActivity implements PermissionHelper.PermissionResultListener, JourneyManager.JourneyLoadListener,   MapManager.OrientationListener  {

    private static final String TAG = "MainActivity";
    private static final String TAG_SYNC = "StateSyncDebug";
    private static final String TAG_LOAD = "PolylineLoadDebug";
    private static final String TAG_ROTATION = "MapRotationDebug";
    private static final String TAG_MATCH_CHECK = "JourneyDisplay";


    private MapView mapView; // MapLibre MapView
    private MapLibreMap maplibreMap; // MapLibre Map object
    private LocationManager locationManager;


    // --- Settings values (Loaded from SharedPreferences) ---
    private int walkingColor = Color.GREEN;
    private int bicyclingColor = Color.BLUE;
    private int inVehicleColor = Color.RED;
    private int polylineThickness = 5;

    // --- Communication & UI Helpers ---
    private LocationBroadcastReceiver locationReceiver;
    private Button gpsStatusButton;
    private ImageView transportModeIcon;
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());


    private FloatingActionButton startStopFab;
    private boolean isTrackingActive = false;
    private SwitchMaterial trackingModeSwitch;

    // Define constants for preference
    public static final String KEY_TRACKING_MODE = "trackingMode";
    public static final String MODE_AUTO = "auto";
    public static final String MODE_MANUAL = "manual";
    private String overrideMode = null;
    public static final String ACTION_MODE_OVERRIDE = "com.example.roots_d01.action.MODE_OVERRIDE";
    public static final String EXTRA_OVERRIDE_MODE = "com.example.roots_d01.extra.OVERRIDE_MODE";
    private ActivityResultLauncher<Intent> settingsLauncher;
    private LocationTrackingService mService;
    private boolean mBound = false;
    private static final String STATUS_CHECKING_ACTIVITY = "Checking Activity...";
    private AlertDialog gpsStatusDialog = null;
    private static final String STATE_IS_TRACKING = "IS_TRACKING_ACTIVE";
    private static final String STATE_OVERRIDE_MODE = "OVERRIDE_MODE";
    private List<JourneyDetails> displayedJourneyDetailsList = new ArrayList<>(); // Stores details for displayed journeys
    private CardView journeyDetailsPanel;
    private TextView tvBottomJourneyMode;
    private TextView tvBottomJourneyStartTime;
    private TextView tvBottomJourneyEndTime;
    private TextView tvBottomJourneyDuration;
    private TextView tvBottomJourneyDistance;
    private Button btnPrevJourney;
    private Button btnNextJourney;
    private MaterialButton btnCloseDetailsPanel;


    // --- Highlighting State (MapLibre adaptation needed) ---
    private int originalHighlightedColor; // May need rework for MapLibre properties
    private float originalHighlightedWidth; // May need rework for MapLibre properties
    private int currentlyDisplayedDetailIndex = -1; // Track index shown in panel

    // --- Highlighting Style ---
    private final int HIGHLIGHT_COLOR = Color.CYAN;
    private final float HIGHLIGHT_WIDTH_INCREASE = 6f; // How much wider to make the line
    private TextView tvBottomJourneyModeBreakdown;
    private Button btnDeleteJourney;

    // --- Valhalla / Retrofit Variables ---
    private Retrofit retrofit;
    private ValhallaService valhallaService;
    private final String VALHALLA_API_URL = "https://valhalla1.openstreetmap.de/trace_attributes";
    public static final String EXTRA_SELECTED_JOURNEY_START_TIME = "com.example.roots_d01.SELECTED_JOURNEY_START_TIME";
    private TextView tvBottomJourneyName;
    private MaterialButton btnEditJourneyName;
    private final Gson gson = new Gson();
    private MapManager mapManager;
    private UiUpdater uiUpdater;
    private PermissionHelper permissionHelper;
    private ActivityRecognitionHelper activityRecognitionHelper;
    private TextView tvBottomJourneyAccuracy;
    private JourneyManager journeyManager;
    private int highlightedJourneyIndex = -1; // Track which journey index is highlighted
    private static final float MAX_DISTANCE_GAP_METERS_FOR_FORCED_MATCHING = 150.0f;
    private static final long GAP_TIME_THRESHOLD_MS = 3 * 60 * 1000;
    private static final int GAP_MATCH_CONTEXT_POINTS = 2;

    private boolean isJourneyDataLoaded = false;
    private CurrentPolylineAnimator currentPolylineAnimator; // Needs rewrite for MapLibre
    public static final String ACTION_ALL_ACTIVITIES_UPDATE = "com.example.roots_d01.action.ALL_ACTIVITIES_UPDATE";
    public static final String EXTRA_ALL_ACTIVITIES = "com.example.roots_d01.extra.ALL_ACTIVITIES";
    private List<DetectedActivity> lastDetectedActivities = new ArrayList<>();
    private ImageButton setNorthButton;
    private boolean isOrientationLocked = true;
    private View rootView;
    private PolylinePathAnimator polylinePathAnimator = null;
    private HeatmapToggleManager heatmapToggleManager;
    private boolean isHeatmapModeActive = false;

    private static final String CURRENT_TRACK_SOURCE_ID = "current-track-source";
    private static final String CURRENT_TRACK_LAYER_ID = "current-track-layer";
    private List<Point> currentTrackMapPoints = new ArrayList<>(); // To store points for the current track visualization
    public static final String HISTORICAL_LAYER_PREFIX = "historical-journey-layer-";
    private int historicalVisibilitySetting = SettingsActivity.MODE_SHOW_ALL; // Default

    private static final long MAX_TIME_GAP_MS = 10 * 60 * 1000; // 10 minutes
    private static final float MAX_DISTANCE_GAP_METERS = 500.0f; // 500 meters
    private static final Map<String, String> PREDEFINED_STYLE_URLS = new HashMap<>();
    static {
        PREDEFINED_STYLE_URLS.put("OSM Bright", "https://api.maptiler.com/maps/streets-v2/style.json?key=dnuxooBP2JxDA2AXS2AD");
        PREDEFINED_STYLE_URLS.put("Streets", "https://api.maptiler.com/maps/streets-v2/style.json?key=dnuxooBP2JxDA2AXS2AD"); // Replace key!
        PREDEFINED_STYLE_URLS.put("Outdoors", "https://api.maptiler.com/maps/outdoor-v2/style.json?key=dnuxooBP2JxDA2AXS2AD"); // Replace key!
        PREDEFINED_STYLE_URLS.put("Satellite Streets", "https://api.maptiler.com/maps/hybrid/style.json?key=dnuxooBP2JxDA2AXS2AD"); // Replace key!
        // Add other valid styles if needed
    }

    private List<LatLng> currentTrackLatLngs = new ArrayList<>(); // Maintain current points locally


    private boolean filterAllActive = true; // Controls the "All" chip state
    private boolean filterWalkActive = true;
    private boolean filterBikeActive = true;
    private boolean filterVehicleActive = true;

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        MapLibre.getInstance(this);

        // Apply MapLibre Cache Size from Settings
        try {
            SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
            // Use the keys and defaults defined in SettingsActivity
            int savedCacheSizeMB = prefs.getInt(SettingsActivity.KEY_MAP_CACHE_SIZE_MB, SettingsActivity.DEFAULT_MAP_CACHE_SIZE_MB);
            long cacheSizeBytes = (long) savedCacheSizeMB * 1024 * 1024; // Convert MB to Bytes
            org.maplibre.android.offline.OfflineManager offlineManager = org.maplibre.android.offline.OfflineManager.getInstance(this);
            offlineManager.setMaximumAmbientCacheSize(cacheSizeBytes, new org.maplibre.android.offline.OfflineManager.FileSourceCallback() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Successfully set MapLibre ambient cache size to " + savedCacheSizeMB + " MB");
                }

                @Override
                public void onError(@NonNull String message) {
                    Log.e(TAG, "Error setting MapLibre ambient cache size: " + message);
                    // Optionally show a non-critical Toast to the user
                    // Toast.makeText(MainActivity.this, "Could not set map cache size.", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error applying MapLibre cache size setting in onCreate", e);
        }



        handleIntent(getIntent());

        // --- Restore state ---
        if (savedInstanceState != null) {
            Log.d(TAG, "onCreate: Restoring saved instance state.");
            isTrackingActive = savedInstanceState.getBoolean(STATE_IS_TRACKING, false);
            overrideMode = savedInstanceState.getString(STATE_OVERRIDE_MODE, null);
            Log.d(TAG, "onCreate: Restored state - isTrackingActive=" + isTrackingActive + ", overrideMode=" + overrideMode);
        } else {
            Log.d(TAG, "onCreate: No saved instance state found.");
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // --- Set Layout ---
        setContentView(R.layout.activity_main);

        // --- Find MapView (Now MapLibre's MapView) ---
        mapView = findViewById(R.id.mapview); // Should find the org.maplibre.android.maps.MapView

        // --- Handle MapView Lifecycle ---
        mapView.onCreate(savedInstanceState); // Pass savedInstanceState

        // Inside the onCreate method:
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapLibreMap map) { // 'map' is the parameter
                Log.i(TAG, ">>> onMapReady: MapLibreMap callback received.");
                MainActivity.this.maplibreMap = map; // Assign to class member
                Log.d(TAG, "onMapReady: Assigned 'map' parameter to 'MainActivity.this.maplibreMap'.");

                // --- Get Last Known Location (BEFORE restoring state) ---
                LatLng initialUserLatLng = null; // Variable to hold initial location
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        if (locationManager != null) {
                            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (lastKnownLocation == null) {
                                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                            }
                            if (lastKnownLocation != null) {
                                initialUserLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                                Log.i(TAG, "onMapReady: Got Last Known Location: " + initialUserLatLng);
                            } else {
                                Log.w(TAG, "onMapReady: Last Known Location is null.");
                            }
                        } else {
                            Log.e(TAG, "onMapReady: LocationManager is null, cannot get last known location.");
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "onMapReady: SecurityException getting last known location.", e);
                    } catch (Exception e) {
                        Log.e(TAG, "onMapReady: Exception getting last known location.", e);
                    }
                } else {
                    Log.w(TAG, "onMapReady: Location permission not granted, cannot get last known location for initial position.");
                }

                // Configure UI settings using the 'map' parameter
                try {
                    Log.d(TAG, "onMapReady: Configuring UI Settings using 'map' parameter...");
                    map.getUiSettings().setCompassEnabled(false);
                    map.getUiSettings().setLogoEnabled(true);
                    map.getUiSettings().setAttributionEnabled(true);
                    map.getUiSettings().setTiltGesturesEnabled(true);
                } catch (Exception e) {
                    Log.e(TAG, "onMapReady: Error configuring initial UI settings", e);
                }

                // Restore Camera State using the 'map' parameter
                Log.d(TAG, "onMapReady: Restoring map state...");
                restoreMapState(map, initialUserLatLng); // Pass the location if found

                // Initialize helper classes using the class member 'maplibreMap'
                if (MainActivity.this.maplibreMap != null) {
                    Log.d(TAG, "onMapReady: Initializing animators/managers using 'maplibreMap' member...");
                    polylinePathAnimator = new PolylinePathAnimator(MainActivity.this.maplibreMap);
                    heatmapToggleManager = new HeatmapToggleManager(MainActivity.this.maplibreMap);
                    // Initialize MapManager here if not already done, passing the map
                    if (mapManager == null && mapView != null && locationManager != null) {
                        mapManager = new MapManager(MainActivity.this, mapView, locationManager);
                        Log.d(TAG, "onMapReady: Initialized MapManager.");
                    }
                } else {
                    Log.e(TAG, "onMapReady: maplibreMap member is null after assignment! Cannot initialize helpers.");
                }

                // --- Style Loading Logic ---
                Log.d(TAG, "onMapReady: Starting style loading logic...");
                String styleUrlToLoad; // Declare variable here

                try {
                    // Determine the style URL
                    if (BuildConfig.MAPBOX_PUBLIC_TOKEN == null || BuildConfig.MAPBOX_PUBLIC_TOKEN.trim().isEmpty() || BuildConfig.MAPBOX_PUBLIC_TOKEN.equals("YOUR_PUBLIC_MAPBOX_TOKEN")) {
                        Log.w(TAG, "onMapReady: MAPBOX_PUBLIC_TOKEN missing/empty/placeholder. Using fallback demo style.");
                        styleUrlToLoad = "https://demotiles.maplibre.org/style.json";
                    } else {
                        styleUrlToLoad = "https://api.maptiler.com/maps/streets-v2/style.json?key=" + BuildConfig.MAPBOX_PUBLIC_TOKEN;
                        Log.d(TAG, "onMapReady: Using MapTiler style URL.");
                    }
                    Log.i(TAG, "onMapReady: Attempting to load style: " + styleUrlToLoad);

                    // Define the listener
                    final LatLng finalInitialUserLatLng = initialUserLatLng;
                    Style.OnStyleLoaded styleLoadedCallback = new Style.OnStyleLoaded() {
                        @Override
                        public void onStyleLoaded(@NonNull Style style) {
                            Log.i(TAG, ">>> onStyleLoaded: Style loaded successfully: " + style.getUri());
                            if (MainActivity.this.maplibreMap == null) {
                                Log.e(TAG, "onStyleLoaded: maplibreMap member is null! Cannot proceed.");
                                return;
                            }
                            Log.d(TAG, "onStyleLoaded: Setting up map-dependent features...");
                            setupMapDependentFeatures(style);
                            if (mapManager != null && finalInitialUserLatLng != null) {
                                Log.d(TAG, "onStyleLoaded: Updating initial marker position to: " + finalInitialUserLatLng);
                                // Use "Unknown" as initial mode, service will update it later
                                mapManager.updateUserLocationMarker(finalInitialUserLatLng, "Unknown");
                            } else {
                                Log.w(TAG, "onStyleLoaded: Not setting initial marker position (MapManager="+(mapManager == null)+", InitialLatLng="+(finalInitialUserLatLng == null)+")");
                            }
                            Log.d(TAG, "onStyleLoaded: Adding map click listener...");
                            addMapClickListener(MainActivity.this.maplibreMap); // Use class member

                            Log.d(TAG, "onStyleLoaded: Applying gesture settings...");
                            SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
                            isOrientationLocked = prefs.getBoolean(SettingsActivity.KEY_LOCK_ORIENTATION, true);
                            MainActivity.this.maplibreMap.getUiSettings().setRotateGesturesEnabled(!isOrientationLocked); // Use class member
                            Log.d(TAG_ROTATION, "onStyleLoaded: Applied initial rotate gesture setting: enabled=" + !isOrientationLocked);
                        }
                    };

                    // --- Set the style using the 'map' PARAMETER ---
                    Log.d(TAG, "onMapReady: Calling map.setStyle...");
                    // *** Ensure this line uses styleUrlToLoad ***
                    map.setStyle(new Style.Builder().fromUri(styleUrlToLoad), styleLoadedCallback);
                    Log.d(TAG, "onMapReady: map.setStyle call finished.");

                } catch (Exception e) {
                    Log.e(TAG, ">>> onMapReady: Exception during style setup block: ", e);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error setting up map style.", Toast.LENGTH_LONG).show());
                }
                // --- End Style Loading ---

                Log.d(TAG, "onMapReady: Method execution finished.");
            } // End onMapReady inner method
        }); // End mapView.getMapAsync call

        // --- Get LocationManager (for status checks) ---
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);


        try {
            // *** Instantiate JourneyManager ***
            // Ensure gson is initialized
            journeyManager = new JourneyManager(this, backgroundExecutor, mainThreadHandler, gson, this);
            Log.d(TAG, "JourneyManager instantiated.");

            // ... (rest of onCreate: listeners, initial state applying, loading data, permissions etc.) ...
        } catch (Exception e) {
            Log.e(TAG, "Error initializing JourneyManager.", e);
            Toast.makeText(this, "Error initializing JourneyManager. Please restart.", Toast.LENGTH_LONG).show();
            finish(); // Close the app if JourneyManager fails to initialize
        }

        try {
            mapView = findViewById(R.id.mapview);
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE); // Initialize locationManager here

            if (mapView == null) {
                throw new NullPointerException("MapView not found in layout!");
            }
            if (locationManager == null) {
                throw new NullPointerException("LocationManager service not available!");
            }

            // Instantiate Managers and Animator *after* successful view/service finding
            mapManager = new MapManager(this, mapView, locationManager);
            currentPolylineAnimator = new CurrentPolylineAnimator(mapView, MainActivity.this.maplibreMap); // <<< INSTANTIATE HERE
            Log.d(TAG, "MapManager and CurrentPolylineAnimator instantiated.");

            // Continue with other initialization...
        } catch (Exception e) {
            Log.e(TAG, "Error initializing critical map/location components.", e);
            Toast.makeText(this, "Error initializing map. Please restart.", Toast.LENGTH_LONG).show();
            finish();
            return; // Exit onCreate if critical init fails
        }


        settingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "SettingsActivity Result Received: Code=" + result.getResultCode());

                    // Check if the result code is OK and if the 'tracksCleared' flag is true
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getBooleanExtra("tracksCleared", false)) {
                            Log.i(TAG, "Settings indicate tracks were cleared. Reloading all polylines.");
                            isJourneyDataLoaded = false;
                            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                Log.d(TAG, "Triggering immediate reload after tracks cleared.");
                                if (journeyManager != null) {
                                    journeyManager.loadAllPolylineData(); // Trigger reload now
                                } else {
                                    Log.e(TAG, "Cannot trigger immediate reload, journeyManager is null.");
                                }
                            } else {
                                Log.d(TAG, "Tracks cleared, flag reset. Reload will happen on next permission grant/resume.");
                            }
                        } else {
                            Log.d(TAG, "SettingsActivity returned OK, but tracks not cleared (or flag missing). Visuals will refresh in onResume.");
                            // Check if map style changed etc., might need map refresh but not full data reload
                            // If map style changes require re-applying overlays based on new colors,
                            // you might need to set isJourneyDataLoaded = false here too, or handle differently.
                            // For now, assume only track clearing forces data reload.
                        }
                    } else {
                        Log.d(TAG, "SettingsActivity returned result Canceled or other code: " + result.getResultCode());
                    }

                });

        // --- Initialize UI Elements ---
        try {
            // MapManager needs refactoring, pass MapView reference
            mapManager = new MapManager(this, mapView, locationManager);
            // currentPolylineAnimator needs rewrite for MapLibre
            currentPolylineAnimator = new CurrentPolylineAnimator(mapView, MainActivity.this.maplibreMap);

            mapView = findViewById(R.id.mapview);
            gpsStatusButton = findViewById(R.id.gpsStatusButton);
            transportModeIcon = findViewById(R.id.transportModeIcon);
            startStopFab = findViewById(R.id.startStopFab);
            trackingModeSwitch = findViewById(R.id.trackingModeSwitch);
            tvBottomJourneyMode = findViewById(R.id.tv_bottom_journey_mode);
            tvBottomJourneyStartTime = findViewById(R.id.tv_bottom_journey_start_time);
            tvBottomJourneyEndTime = findViewById(R.id.tv_bottom_journey_end_time);
            tvBottomJourneyDuration = findViewById(R.id.tv_bottom_journey_duration);
            tvBottomJourneyAccuracy = findViewById(R.id.tv_bottom_journey_accuracy);
            tvBottomJourneyDistance = findViewById(R.id.tv_bottom_journey_distance);
            btnPrevJourney = findViewById(R.id.btnPrevJourney);
            btnNextJourney = findViewById(R.id.btnNextJourney);
            btnCloseDetailsPanel = findViewById(R.id.btnCloseDetailsPanel);
            journeyDetailsPanel = findViewById(R.id.journeyDetailsPanel);
            tvBottomJourneyMode = findViewById(R.id.tv_bottom_journey_mode);
            tvBottomJourneyStartTime = findViewById(R.id.tv_bottom_journey_start_time);
            tvBottomJourneyEndTime = findViewById(R.id.tv_bottom_journey_end_time);
            tvBottomJourneyDuration = findViewById(R.id.tv_bottom_journey_duration);
            tvBottomJourneyDistance = findViewById(R.id.tv_bottom_journey_distance);
            btnPrevJourney = findViewById(R.id.btnPrevJourney);
            btnNextJourney = findViewById(R.id.btnNextJourney);
            btnCloseDetailsPanel = findViewById(R.id.btnCloseDetailsPanel);
            tvBottomJourneyModeBreakdown = findViewById(R.id.tv_bottom_journey_mode_breakdown);
            tvBottomJourneyName = findViewById(R.id.tv_bottom_journey_name);
            btnEditJourneyName = findViewById(R.id.btnEditJourneyName);
            setNorthButton = findViewById(R.id.setNorthButton);
            btnDeleteJourney = findViewById(R.id.btnDeleteJourney);




            // *** Instantiate UiUpdater AFTER finding all its required views ***
            uiUpdater = new UiUpdater(this, gpsStatusButton, transportModeIcon,startStopFab, trackingModeSwitch);
            Log.d(TAG, "UiUpdater instantiated.");

            permissionHelper = new PermissionHelper(this, uiUpdater, this);
            Log.d(TAG, "PermissionHelper instantiated.");
            if (permissionHelper != null) {
                permissionHelper.checkAndRequestBasePermissions();
            }
            activityRecognitionHelper = new ActivityRecognitionHelper(this);
            journeyManager = new JourneyManager(this, backgroundExecutor, mainThreadHandler, gson, this);

            Log.d(TAG, "Helpers Initialized");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing helper components.", e);
            Toast.makeText(this, "Error initializing components. Please restart.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

            setupRetrofit();
            setupSettingsLauncher();
        Log.d(TAG, "onCreate: Calling setupBottomPanelListeners...");
        setupBottomPanelListeners();
            setupOtherListeners();
            loadAndApplyInitialUiState();
            loadSettings(); // Load non-map settings

            // Permissions & Activity Recognition

            requestActivityUpdatesPermission();
            locationReceiver = new LocationBroadcastReceiver();
            setupRootViewInsets();


            // Example: Update initial GPS indicator state using the updater
            if (uiUpdater != null) {
                uiUpdater.updateGPSIndicator(0); // Set initial state to Red/No GPS
            }
            Log.d(TAG, "UI Elements Initialized & Helpers Instantiated");


            // --- Find Bottom Panel Views ---
            journeyDetailsPanel = findViewById(R.id.journeyDetailsPanel);
            if (journeyDetailsPanel == null)
                Log.e(TAG, "onCreate: journeyDetailsPanel is NULL after findViewById!");


            if (tvBottomJourneyModeBreakdown == null)
                Log.e(TAG, "onCreate: tvBottomJourneyModeBreakdown is NULL after findViewById!");

            if (tvBottomJourneyModeBreakdown == null) {
                throw new NullPointerException("tv_bottom_journey_mode_breakdown not found");
            }

            if (journeyDetailsPanel == null || tvBottomJourneyMode == null | tvBottomJourneyName == null || btnEditJourneyName == null) {
                throw new NullPointerException("One or more bottom journey panel views (name/edit) not found...");
            }



            // --- Apply restored state to UI elements IMMEDIATELY ---
            Log.d(TAG, "onCreate: Updating UI based on potentially restored state.");
        if (uiUpdater != null) {
            // Pass false for isTrackingActive and "Still" for effectiveMode
            uiUpdater.updateStartStopButtonState(isTrackingActive, "Still");
        } else {
            Log.w(TAG, "UiUpdater is null in stopTracking.");
        }            String modeFromPrefsLine388 = getSharedPreferences("Settings", MODE_PRIVATE).getString(KEY_TRACKING_MODE, "auto"); // Read mode
            if (uiUpdater != null) {
                uiUpdater.updateUiBasedOnTrackingMode(modeFromPrefsLine388, isTrackingActive); // Add isTrackingActive
            }            // Update icon based on override or default state if not tracking
            if (overrideMode != null && isTrackingActive) {
                uiUpdater.updateTransportModeIcon(overrideMode);
            } else if (isTrackingActive) {
                // If tracking was active but no override, maybe show "Auto" or last known?
                // For now, let broadcast receiver handle detailed text update later.
                uiUpdater.updateTransportModeIcon(null); // Clear icon until update
            }
            // --- End Apply Restored State ---


            if (rootView != null) {
                ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                    // Get insets for system bars (status bar, navigation bar)
                    Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

                    // Apply insets as padding to the root view
                    v.setPadding(
                            systemBarInsets.left,
                            systemBarInsets.top,    // Pushes content below status bar
                            systemBarInsets.right,
                            systemBarInsets.bottom  // Pushes content above navigation bar
                    );

                    Log.d(TAG, "Applied system bar insets as padding. Bottom: " + systemBarInsets.bottom);

                    // Return the insets to indicate they've been consumed
                    return windowInsets; // Or WindowInsetsCompat.CONSUMED if not nested scrolling
                });
            } else {
                Log.e(TAG, "Root layout (rootFrameLayout) not found!");
            }

        if (uiUpdater != null) {
            // Pass false for isTrackingActive and "Still" for effectiveMode
            uiUpdater.updateStartStopButtonState(isTrackingActive, "Still");
        } else {
            Log.w(TAG, "UiUpdater is null in stopTracking.");
        }
        // --- Check Permissions ---
        if (permissionHelper != null) {
            permissionHelper.checkAndRequestBasePermissions();
        }
        Log.d(TAG, "End of onCreate");
        }

    private void setupOtherListeners() {
        Button recenterButton = findViewById(R.id.recenterButton);
        Button settingsButton = findViewById(R.id.settingsButton);
        MaterialButton activityInfoButton = findViewById(R.id.activityInfoButton);
        Button viewJourneysButton = findViewById(R.id.viewJourneysButton);
        Button btnToggleHeatmap = findViewById(R.id.btnToggleHeatmap);
        Chip chipWalk = findViewById(R.id.chipFilterWalk);
        Chip chipBike = findViewById(R.id.chipFilterBike);
        Chip chipVehicle = findViewById(R.id.chipFilterVehicle);
// Inside onCreate() or a setup method like setupOtherListeners()

        if (chipWalk != null && chipBike != null && chipVehicle != null) {


            CompoundButton.OnCheckedChangeListener individualChipListener = (buttonView, isChecked) -> {
                int id = buttonView.getId();
                if (id == R.id.chipFilterWalk) {
                    filterWalkActive = isChecked;
                    Log.d(TAG, "Filter 'Walk' toggled: " + isChecked);
                } else if (id == R.id.chipFilterBike) {
                    filterBikeActive = isChecked;
                    Log.d(TAG, "Filter 'Bike' toggled: " + isChecked);
                } else if (id == R.id.chipFilterVehicle) {
                    filterVehicleActive = isChecked;
                    Log.d(TAG, "Filter 'Vehicle' toggled: " + isChecked);
                }

                updateHistoricalJourneyVisibility(getCurrentEffectiveMode()); // Update map
            };

            chipWalk.setOnCheckedChangeListener(individualChipListener);
            chipBike.setOnCheckedChangeListener(individualChipListener);
            chipVehicle.setOnCheckedChangeListener(individualChipListener);

        } else {
            Log.e(TAG, "One or more filter chips not found in layout!");
        }


        if (setNorthButton != null) {
            setNorthButton.setOnClickListener(v -> {
                if (mapView != null) {
                    Log.d(TAG, "Set North button clicked. Resetting orientation.");
                    // Optional: Animate back to North
                    // mapView.getController().animateTo(mapView.getMapCenter(), mapView.getZoomLevelDouble(), 500L, 0f);
                }
                // Button visibility will be handled by onOrientationChanged
            });
        } else {
            Log.e(TAG, "setNorthButton not found!");
        }


        activityInfoButton = findViewById(R.id.activityInfoButton);
        if (activityInfoButton != null) {
            activityInfoButton.setOnClickListener(v -> showActivityConfidencePopup());
        } else {
            Log.e(TAG, "activityInfoButton not found in layout!");
        }

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            settingsLauncher.launch(intent);
        });

        startStopFab.setOnClickListener(v -> { // Use the new variable name
            if (isTrackingActive) {
                stopTracking(); // Call method to stop
            } else {
                startTracking(); // Call method to start
            }
        });

        if (startStopFab != null) {
            startStopFab.setOnClickListener(v -> {
                if (isTrackingActive) {
                    stopTracking();
                } else {
                    startTracking();
                }
            });
        } // ... null check else ...

        gpsStatusButton.setOnClickListener(v -> {
            Log.d("DebugGPSButton", "GPS Status Button Clicked!"); // <-- Add Log
            showGPSStatus();
        });

        if (recenterButton != null) { // Add null check for safety
            recenterButton.setOnClickListener(v -> {
                if (mapManager != null) { // Check if mapManager exists
                    mapManager.recenterMap(); // Call the manager's method
                } else {
                    Log.w(TAG, "Recenter button clicked, but mapManager is null.");
                }
            });
        } else {
            Log.e(TAG, "Recenter button not found in layout!");
        }

        if (trackingModeSwitch != null) {
            trackingModeSwitch.setOnCheckedChangeListener(switchListener);
        }

        if (transportModeIcon != null) {
            transportModeIcon.setOnClickListener(view -> {
                boolean isVisible = transportModeIcon.getVisibility() == View.VISIBLE;
                boolean isClickable = transportModeIcon.isClickable();
                Log.d("TIcon", "transportModeIcon onClick triggered! isTrackingActive = " + isTrackingActive + ", isVisible = " + isVisible + ", isClickable = " + isClickable);
                if (isTrackingActive) {
                    showPopupMenu(view);
                } else {
                    // Log why it didn't show
                    Log.d("TIcon", "Popup menu not shown because isTrackingActive is false.");
                    Toast.makeText(this, "Start tracking to override mode", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Log.w("TIcon", "transportModeIcon is null, cannot set listener.");
        }

        if (btnToggleHeatmap != null) {
            btnToggleHeatmap.setOnClickListener(v -> {
                isHeatmapModeActive = !isHeatmapModeActive; // Toggle the flag in MainActivity
                Log.i(TAG, "Toggle button clicked. Heatmap mode active: " + isHeatmapModeActive);

                if (heatmapToggleManager != null) {
                    // ---> CALL MANAGER'S METHOD <---
                    int journeyCount = (displayedJourneyDetailsList != null) ? displayedJourneyDetailsList.size() : 0;
                    heatmapToggleManager.setDisplayMode(isHeatmapModeActive, journeyCount);

                    // ---> IF switching TO heatmap, hide panel/animation <---
                    if (isHeatmapModeActive) {
                        hideJourneyDetailsPanel(); // Already calls resetHighlight
                        if (polylinePathAnimator != null) polylinePathAnimator.stopAnimation();
                    }

                } else {
                    Log.e(TAG,"HeatmapToggleManager is null, cannot set display mode!");
                }

                // Update button appearance (optional)
                btnToggleHeatmap.setBackgroundTintList(ContextCompat.getColorStateList(this,
                        isHeatmapModeActive ? android.R.color.holo_red_dark : R.color.orange));
            });
        } else {
            Log.e(TAG, "btnToggleHeatmap not found!");
        }

        if (setNorthButton != null) {
            setNorthButton.setOnClickListener(v -> {
                // Check if the MapLibre map object is ready
                if (maplibreMap != null) {
                    Log.d(TAG, "Set North button clicked. Resetting orientation.");

                    // Get the current camera position
                    org.maplibre.android.camera.CameraPosition currentPosition = maplibreMap.getCameraPosition();

                    // Create a new camera position with the same target and zoom,
                    // but reset bearing and tilt to 0.
                    org.maplibre.android.camera.CameraPosition newPosition =
                            new org.maplibre.android.camera.CameraPosition.Builder(currentPosition)
                                    .bearing(0) // Reset bearing to North
                                    .tilt(0)    // Reset tilt
                                    .build();

                    // Animate the camera to the new position
                    maplibreMap.easeCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(newPosition),
                            750 // Animation duration in milliseconds (optional)
                    );

                } else {
                    Log.w(TAG, "Set North button clicked, but maplibreMap is null.");
                }
                // Button visibility is handled by onOrientationChanged callback
            });
        } else {
            Log.e(TAG, "setNorthButton not found!");
        }


        viewJourneysButton = findViewById(R.id.viewJourneysButton); // Add this button to your layout
        viewJourneysButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, com.example.roots_d01.JourneyListActivity.class);
            startActivity(intent);
        });
    }









    // --- Helper method called when Style is loaded ---
    private void setupMapDependentFeatures(@NonNull Style style) {
        Log.i(TAG, ">>> setupMapDependentFeatures: START"); // <-- Log Start

        Log.d(TAG, "setupMapDependentFeatures: Initializing base map sources/layers...");
        initializeMapSourcesAndLayers(style); // Create base sources/layers for user marker etc.

        Log.d(TAG, "setupMapDependentFeatures: Setting up animation layer...");
        setupAnimationLayer(style); // Setup layer for path animation

        Log.d(TAG, "setupMapDependentFeatures: Setting up heatmap source/layer...");
        if (heatmapToggleManager != null) {
            heatmapToggleManager.setupHeatmapSourceLayer(style);
        } else {
            Log.e(TAG,"setupMapDependentFeatures: HeatmapToggleManager is null during setup!");
        }

        // Trigger journey loading only if permission is granted
        Log.d(TAG, "setupMapDependentFeatures: Checking permission before loading journeys...");
        // Use the member 'permissionHelper' if available, otherwise check directly
        boolean hasPermission = (permissionHelper != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        // Fallback check just in case helper is null
        if (permissionHelper == null) {
            hasPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            Log.w(TAG, "setupMapDependentFeatures: PermissionHelper is null, checking permission directly.");
        }


        if (hasPermission) {
            if (journeyManager != null) {
                Log.i(TAG, "setupMapDependentFeatures: Permission GRANTED. Triggering journeyManager.loadAllPolylineData()..."); // <-- Log Trigger
                journeyManager.loadAllPolylineData(); // Load historical data
            } else {
                Log.e(TAG, "setupMapDependentFeatures: Permission GRANTED but journeyManager is null!"); // <-- Log Error
            }
        } else {
            Log.w(TAG, "setupMapDependentFeatures: Location permission not granted at this point, skipping journey load."); // <-- Log Skip
            // Optionally: Request permission again here? Or rely on user granting it later?
            // if (permissionHelper != null) permissionHelper.checkAndRequestBasePermissions();
        }
        Log.i(TAG, ">>> setupMapDependentFeatures: END"); // <-- Log End
    }

    // Inside MainActivity.java
    private void initializeMapSourcesAndLayers(@NonNull Style style) {
        Log.d(TAG, "Initializing base map sources and layers.");
        if (mapManager != null) {
            mapManager.initializeMapLibreComponents(maplibreMap, style); // Delegate user marker etc.
        }

        // --- Add Source and Layer for the CURRENT track ---
        if (style.getSource(CURRENT_TRACK_SOURCE_ID) == null) {
            // Initialize with an empty LineString feature
            GeoJsonSource currentTrackSource = new GeoJsonSource(CURRENT_TRACK_SOURCE_ID,
                    Feature.fromGeometry(LineString.fromLngLats(new ArrayList<>())));
            style.addSource(currentTrackSource);
            Log.d(TAG, "Added source for current track: " + CURRENT_TRACK_SOURCE_ID);
        }

        if (style.getLayer(CURRENT_TRACK_LAYER_ID) == null) {
            LineLayer currentTrackLayer = new LineLayer(CURRENT_TRACK_LAYER_ID, CURRENT_TRACK_SOURCE_ID);
            currentTrackLayer.setProperties(
                    PropertyFactory.lineColor(Color.BLUE), // Default color, will be updated
                    PropertyFactory.lineWidth(polylineThickness * 1.5f), // Make it slightly thicker?
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.visibility(Property.VISIBLE) // Make sure it's visible
            );
            // Add the layer (consider adding below labels if needed, like historical ones)
            // For simplicity, add it on top for now. Adjust later if it covers things.
            style.addLayer(currentTrackLayer);
            Log.d(TAG, "Added layer for current track: " + CURRENT_TRACK_LAYER_ID);
        }
        // --- End Current Track Source/Layer ---
    }


    /**
     * Initializes the MapLibre source and layer for displaying the currently recorded track.
     * Should be called when tracking starts or a new segment begins (e.g., mode override).
     *
     * @param initialTransportMode Hint for the initial color/style (e.g., "Walking", "Unknown").
     */
    private void initializeCurrentPolyline(String initialTransportMode) {
        Log.d(TAG, "Initializing current polyline visualization for mode: " + initialTransportMode);
        currentTrackLatLngs.clear(); // Clear local points

        if (maplibreMap == null) {
            Log.e(TAG, "MapLibreMap is null in initializeCurrentPolyline. Cannot setup source/layer.");
            return;
        }
        Style style = maplibreMap.getStyle();
        if (style == null || !style.isFullyLoaded()) {
            Log.w(TAG, "Style not ready in initializeCurrentPolyline. Source/layer setup deferred.");
            // If the style loads later, we might need to call this again or handle setup there.
            return;
        }

        // Ensure Source exists
        GeoJsonSource currentSource = style.getSourceAs(CURRENT_TRACK_SOURCE_ID);
        if (currentSource == null) {
            // Start with an empty LineString feature
            Feature emptyFeature = Feature.fromGeometry(LineString.fromLngLats(new ArrayList<>()));
            currentSource = new GeoJsonSource(CURRENT_TRACK_SOURCE_ID, emptyFeature);
            style.addSource(currentSource);
            Log.i(TAG, "Added GeoJsonSource for current track: " + CURRENT_TRACK_SOURCE_ID);
        } else {
            // Clear existing data in the source
            Feature emptyFeature = Feature.fromGeometry(LineString.fromLngLats(new ArrayList<>()));
            currentSource.setGeoJson(emptyFeature);
            Log.d(TAG, "Cleared existing GeoJsonSource for current track: " + CURRENT_TRACK_SOURCE_ID);
        }

        // Ensure Layer exists and set initial style
        LineLayer currentLayer = style.getLayerAs(CURRENT_TRACK_LAYER_ID);
        int initialColor = getColorForTransport(initialTransportMode); // Use helper
        float initialWidth = (float) polylineThickness; // Use loaded setting

        if (currentLayer == null) {
            currentLayer = new LineLayer(CURRENT_TRACK_LAYER_ID, CURRENT_TRACK_SOURCE_ID);
            currentLayer.setProperties(
                    PropertyFactory.lineColor(initialColor),
                    PropertyFactory.lineWidth(initialWidth),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.visibility(Property.VISIBLE) // Make sure it's visible
            );
            // Add the layer below labels if possible
            String labelLayerId = findFirstLabelLayerId(style); // Need this helper in MainActivity or MapManager
            if (labelLayerId != null) {
                style.addLayerBelow(currentLayer, labelLayerId);
                Log.i(TAG, "Added LineLayer '" + CURRENT_TRACK_LAYER_ID + "' below '" + labelLayerId + "' for current track.");
            } else {
                style.addLayer(currentLayer); // Fallback add on top
                Log.w(TAG, "Added LineLayer '" + CURRENT_TRACK_LAYER_ID + "' on top (no label layer found).");
            }
        } else {
            // Update existing layer's style
            currentLayer.setProperties(
                    PropertyFactory.lineColor(initialColor),
                    PropertyFactory.lineWidth(initialWidth),
                    PropertyFactory.visibility(Property.VISIBLE)
            );
            Log.d(TAG, "Updated existing LineLayer style for current track: " + CURRENT_TRACK_LAYER_ID);
        }

        // Update the animator target
        if (currentPolylineAnimator != null) {
            // Check if layer actually exists now before setting target
            if (style.getLayer(CURRENT_TRACK_LAYER_ID) != null) {
                currentPolylineAnimator.setTargetLayerId(CURRENT_TRACK_LAYER_ID); // <<< SET TARGET
                Log.i(TAG, "Animator target SET to: " + currentPolylineAnimator.getTargetLayerId()); // <-- MAKE SURE THIS LOG IS PRESENT
                // Start blinking ONLY if tracking is already active AND we just initialized the layer
                if (isTrackingActive) { // Check MainActivity's tracking state
                    Log.d(TAG, "initializeCurrentPolyline: Tracking active, starting blinker.");
                    currentPolylineAnimator.startBlinking(); // Start immediately if needed
                }
            } else {
                Log.e(TAG, "initializeCurrentPolyline: Layer " + CURRENT_TRACK_LAYER_ID + " still null after creation/update attempt. Cannot set animator target.");
                if (currentPolylineAnimator.getTargetLayerId() != null) { // Clear old target if layer setup failed
                    currentPolylineAnimator.setTargetLayerId(null);
                }
            }
        } else {
            Log.w(TAG,"initializeCurrentPolyline: currentPolylineAnimator is null.");
        }
    }

    // Helper method (can be moved to MapManager or kept here)
    private String findFirstLabelLayerId(Style style) {
        String[] commonLabelLayerIds = {"road-label", "highway-label", "place-label", "poi-label", "waterway-label"};
        for (String id : commonLabelLayerIds) {
            if (style.getLayer(id) instanceof SymbolLayer) {
                return id;
            }
        }
        for (Layer layer : style.getLayers()) {
            if (layer instanceof SymbolLayer && layer.getId().contains("label")) {
                return layer.getId();
            }
        }
        for (Layer layer : style.getLayers()) {
            if (layer instanceof SymbolLayer) { return layer.getId(); }
        }
        return null; // No suitable layer found
    }

    // --- Placeholder for restoring map state ---
// (Keep the restoreMapState placeholder from the previous step)
    private void restoreMapState(MapLibreMap map, LatLng initialUserLatLng) {
        Log.d(TAG, "Restoring map state (Zoom/Center).");
        if (mapManager != null) {
            mapManager.restoreMapState(map); // Delegate to manager
        } else {
            Log.w(TAG, "MapManager null in restoreMapState.");
            // Fallback default
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(51.5074, -0.1278), 14.0));
        }
    }

    // --- Lifecycle Methods ---

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
        Intent intent = new Intent(this, LocationTrackingService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "--------- onResume START ---------"); // <<< ADD LOG START
        super.onResume();
        Log.d(TAG_LOAD, "MainActivity onResume CALLED");


        // Load settings *before* deciding on sensor registration
        loadSettings();

        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        isOrientationLocked = prefs.getBoolean(SettingsActivity.KEY_LOCK_ORIENTATION, true);


        if (mapManager != null) {
            mapManager.setOrientationListener(this); // Set listener regardless of lock

            if (isOrientationLocked) {
                mapManager.unregisterSensorListener();
                // Disable manual rotation gesture if map is ready
                if (maplibreMap != null) {
                    // *** THIS IS THE KEY LINE for onResume ***
                    maplibreMap.getUiSettings().setRotateGesturesEnabled(false);
                } else {
                }
                if (setNorthButton != null) setNorthButton.setVisibility(View.GONE);

            } else { // Orientation Unlocked
                mapManager.registerSensorListener();
                // Enable manual rotation gesture if map is ready
                if (maplibreMap != null) {
                    // *** THIS IS THE KEY LINE for onResume ***
                    maplibreMap.getUiSettings().setRotateGesturesEnabled(true);
                } else {
                }
            }
        } else {
        }


        if (mapManager != null) { // Check if mapManager is initialized
            if (mapView != null) {
                mapView.onResume(); // Resume osmdroid map rendering
            }
            loadSettings(); // Re-load settings needed by MainActivity (Keep for now)
            Log.d(TAG, "onResume: Calling loadAndApplyInitialUiState"); // <<< ADD LOG before call
            loadAndApplyInitialUiState(); // Update UI (Keep for now)
            mapManager.registerSensorListener();
        } else {
            Log.w(TAG, "onResume: mapManager is null. Cannot apply tile source or update UI state.");
            // Still call mapView.onResume() if mapView exists, even if manager is null
            if (mapView != null) {
                mapView.onResume();
            }
        }

        // --- Register Receiver ---
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationTrackingService.ACTION_LOCATION_BROADCAST);
        filter.addAction(LocationTrackingService.ACTION_GPS_DISABLED);
        filter.addAction(LocationTrackingService.ACTION_LOCATION_PERMISSION_ERROR);
        filter.addAction(LocationTrackingService.ACTION_TRACKING_STATE_CHANGED);
        filter.addAction(LocationTrackingService.ACTION_ACTIVITY_DETECTED);
        filter.addAction(MainActivity.ACTION_ALL_ACTIVITIES_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(locationReceiver, filter);
        Log.d(TAG, "Location BroadcastReceiver Registered");


        if (permissionHelper != null) {
            permissionHelper.checkAndRequestBasePermissions(); // <<< ADD THIS CALL
        } else {
            Log.e(TAG, "onResume: permissionHelper is null, cannot check permissions.");
        }
        // Ensure user marker exists via the manager
        requestActivityUpdatesPermission();

        // **Crucially, the request to update the *current* polyline
        // is now triggered from onServiceConnected *or* can be called here
        // if already bound.**
        if (mBound && mService != null) {
            Log.d(TAG, "onResume: Already bound, triggering polyline update.");
            updateCurrentPolylineFromService();
        } else {
            Log.d(TAG, "onResume: Not bound yet, update will trigger onServiceConnected.");
            // Binding is initiated in onStart, onServiceConnected will call updateCurrentPolylineFromService
        }
        Log.d(TAG, "--------- onResume END ---------");
    }


    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
        unregisterReceiver();
        if (activityRecognitionHelper != null) {
            activityRecognitionHelper.removeActivityUpdates();
        }
        // Unregister sensor listener if orientation is unlocked
        if (!isOrientationLocked && mapManager != null) {
            mapManager.unregisterSensorListener();
        }
    }



    @Override
    protected void onStop() {
        super.onStop(); // Call super first
        if (mapView != null) mapView.onStop();
        if (mBound) {
            try { // Add try-catch for safety
                unbindService(connection);
                mBound = false;
                Log.d(TAG, "Unbound from service in onStop.");
            } catch (IllegalArgumentException e) {
                Log.w(TAG,"Error unbinding service in onStop (already unbound?): " + e.getMessage());
            }
        }
        if (mapManager != null) {
            mapManager.saveMapState(); // Delegate saving state
        }
        if (polylinePathAnimator != null) {
            polylinePathAnimator.stopAnimation();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState: Saving state - isTrackingActive=" + isTrackingActive + ", overrideMode=" + overrideMode);
        // Save our custom state into the bundle
        outState.putBoolean(STATE_IS_TRACKING, isTrackingActive);
        outState.putString(STATE_OVERRIDE_MODE, overrideMode);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind service just in case it's still bound
        if (mBound) {
            try {
                unbindService(connection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG,"Error unbinding service in onDestroy (already unbound?): " + e.getMessage());
            }
            mBound = false;
        }
        Log.d(TAG, "onDestroy");
        if (mapView != null) mapView.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
        if (polylinePathAnimator != null) {
            polylinePathAnimator.stopAnimation();
        }
    }

    // --- Permission Handling ---


    private void requestActivityUpdatesPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Requesting Activity Recognition permission.");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                        PermissionHelper.ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE); // Use PermissionHelper.CONSTANT            } else {
                // On older versions, permission is granted at install time if declared in Manifest
                Log.d(TAG, "Activity Recognition permission assumed granted (pre-Q). Requesting updates.");
                if (activityRecognitionHelper != null) {
                    activityRecognitionHelper.requestActivityUpdatesInternal(); // Delegate call
                }
            }
        } else {
            Log.d(TAG, "Activity Recognition permission already granted. Requesting updates.");
            if (activityRecognitionHelper != null) {
                activityRecognitionHelper.requestActivityUpdatesInternal(); // Delegate call
            }
        }
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION})
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Keep super call
        // Delegate handling to the helper
        if (permissionHelper != null) {
            permissionHelper.handlePermissionsResult(requestCode, permissions, grantResults);
        } else {
            Log.e(TAG, "onRequestPermissionsResult: permissionHelper is null!");
        }
    }

    @Override
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    // Add annotation if needed by called methods
    public void onLocationPermissionGranted() {
        Log.d(TAG, "Listener: onLocationPermissionGranted");
        final String TAG_LOAD = "JourneyDisplay"; // Use this tag for consistency
        startLocationService(); // Safe to start service now
        updateMapToLastKnownLocation(); // Update map state now

        // Trigger the separate Activity Recognition check AFTER location is granted
        requestActivityUpdatesPermission();

        Log.i(TAG_LOAD, "Location permission granted. Triggering journeyManager.loadAllPolylineData()."); // <-- MODIFY/ADD Log
        if (!isJourneyDataLoaded) { // <<< Check the flag
            Log.i(TAG_LOAD, "Location permission granted and journey data not loaded yet. Triggering journeyManager.loadAllPolylineData().");
            if (journeyManager != null) {
                journeyManager.loadAllPolylineData(); // Call load only if flag is false
            } else {
                Log.e(TAG_LOAD, "onLocationPermissionGranted: journeyManager is null, cannot load data.");
            }
        } else {
            Log.i(TAG_LOAD, "Location permission granted, but journey data already loaded. Skipping reload.");
        }
    }

    @Override
    public void onLocationPermissionDenied() {
        Log.w(TAG, "Listener: onLocationPermissionDenied");
        // Show Toast or Dialog explaining the need for permission
        Toast.makeText(this, "Location permission is required for tracking.", Toast.LENGTH_LONG).show();
        // The UI indicator update is already handled within PermissionHelper
    }

    @Override
    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION) // Add annotation if needed
    public void onActivityRecognitionPermissionGranted() {
        Log.d(TAG, "Listener: onActivityRecognitionPermissionGranted");
        // Now safe to request AR updates
        if (activityRecognitionHelper != null) {
            activityRecognitionHelper.requestActivityUpdatesInternal(); // Delegate call
        }
    }

    @Override
    public void onActivityRecognitionPermissionDenied() {
        Log.w(TAG, "Listener: onActivityRecognitionPermissionDenied");
        // Show feedback to the user
        Toast.makeText(this, "Activity recognition disabled. Mode detection may be limited.", Toast.LENGTH_SHORT).show();
    }


    // --- Helper for Last Known Location ---

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private void updateMapToLastKnownLocation() {
        if (locationManager == null) return;
        Location lastKnownLocation = null;
        try { // ... get location ...
            lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation == null) lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (SecurityException e) { /* ... */ return;}

        if (lastKnownLocation != null) {
            LatLng lastLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()); // Use LatLng

            // Don't center map here, restoreMapState or recenter button handles it
            // boolean wasRestored = //... REMOVE osmdroid flag logic

            // Update marker using MapManager (needs MapLibre implementation)
            if (mapManager != null) {
                mapManager.updateUserLocationMarker(lastLatLng, "Unknown");
            } // ...

            if (uiUpdater != null) uiUpdater.updateGPSIndicatorFromLocation(lastKnownLocation);
        } else { // ...
            if (uiUpdater != null) uiUpdater.updateGPSIndicator(0);
        }
    }


    /**
     * Fetches current track points from service and updates the visual representation.
     * Needs complete rewrite for MapLibre.
     */
    private void updateCurrentPolylineFromService() {
        if (!mBound || mService == null) { /* ... */ return; }

        Log.d(TAG, "Requesting current track points from service...");
        List<PolylinePoint> currentPoints = mService.getCurrentTrackPoints();
        Log.d(TAG, "Received " + (currentPoints != null ? currentPoints.size() : "null") + " points from service.");

        if (mapView == null || maplibreMap == null || maplibreMap.getStyle() == null) { // Add MapLibre checks
            Log.e(TAG, "Map/Style not ready in updateCurrentPolylineFromService.");
            return;
        }

        // TODO: Implement MapLibre Update Logic:
        // 1. Get the GeoJsonSource for the current polyline (e.g., "current-polyline-source").
        // 2. Convert 'currentPoints' (List<PolylinePoint>) to List<Point> for GeoJSON LineString.
        // 3. Update the source: currentPolylineSource.setGeoJson(LineString.fromLngLats(listOfMapboxPoints));
        // 4. Ensure the corresponding LineLayer is visible and styled correctly.
        Log.w(TAG, "updateCurrentPolylineFromService: MapLibre source update logic needed.");

        // Update animator (needs adaptation)
        if (currentPolylineAnimator != null) {
            Log.i(TAG, "Calling startBlinking() from startTracking()"); // <-- ADD LOG
            if (isTrackingActive) {
                currentPolylineAnimator.startBlinking();
            }
        }

        mapView.invalidate(); // Keep invalidate
        Log.d(TAG, "MapView invalidated after updating current polyline from service.");
    }


    private int getColorForTransport(String transportMode) {
        // Use final variables for colors loaded in loadSettings()
        if (transportMode == null) return Color.DKGRAY; // Handle null

        // Assuming MODE_DETERMINING defined in LocationTrackingService
        switch (transportMode) {
            case "Walking":
                return walkingColor;
            case "Bicycling":
                return bicyclingColor;
            case "In Vehicle":
                return inVehicleColor;
            default: // Includes "Unknown" and any other unexpected string
                return Color.DKGRAY;
        }
    }


    private void startLocationService() {
        // Permissions should be checked before calling this
        Intent serviceIntent = new Intent(this, LocationTrackingService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "Attempted to start LocationTrackingService.");
        } catch (Exception e) {
            Log.e(TAG, "Error starting LocationTrackingService", e);
        }
    }


    private void loadSettings() {
        try {
            SharedPreferences preferences = getSharedPreferences("Settings", MODE_PRIVATE);
            polylineThickness = preferences.getInt("polylineThickness", 5);
            walkingColor = preferences.getInt("walkingColor", Color.GREEN);
            bicyclingColor = preferences.getInt("bicyclingColor", Color.BLUE);
            inVehicleColor = preferences.getInt("inVehicleColor", Color.RED);
            historicalVisibilitySetting = preferences.getInt(
                    SettingsActivity.KEY_HISTORICAL_VISIBILITY_MODE,
                    SettingsActivity.DEFAULT_HISTORICAL_VISIBILITY_MODE
            );
            Log.i(TAG, "loadSettings: Loaded historicalVisibilitySetting = " + historicalVisibilitySetting
                    + " (0=ShowAll, 1=HideAll, 2=HideUnrelated)");
            Log.d(TAG, "Settings loaded: Thickness=" + polylineThickness);
            // Apply thickness to current polyline immediately if it exists
        } catch (Exception e) {
            Log.e(TAG, "Error loading settings", e);
        }
    }

    // GPS Status Dialog
    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION})
    private void showGPSStatus() {
        Log.d("DebugGPSButton", "showGPSStatus() called.");

        if (locationManager == null) {
            Log.e("DebugGPSButton", "locationManager is NULL in showGPSStatus!");
            // Dismiss any old dialog before showing error
            if (gpsStatusDialog != null && gpsStatusDialog.isShowing()) gpsStatusDialog.dismiss();
            gpsStatusDialog = showDialog("GPS Status Error", "Could not access Location Manager."); // Store ref
            return;
        }

        final StringBuilder gpsInfoBuilder = new StringBuilder();
        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        gpsInfoBuilder.append(isGpsEnabled ? "✅ GPS Provider: Enabled\n" : "❌ GPS Provider: Disabled\n");

        if (isGpsEnabled) {
            try {
                Log.d("DebugGPSButton", "Attempting to get last known location...");
                Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc != null) { /* ... (append location info as before) ... */
                    if (loc.hasAccuracy()) {
                        gpsInfoBuilder.append(String.format("📍 Last Accuracy: %.1f m\n", loc.getAccuracy()));
                    } else {
                        gpsInfoBuilder.append("📍 Last Accuracy: N/A\n");
                    }
                    gpsInfoBuilder.append(String.format("🕒 Last Fix: %.1f sec ago\n", (System.currentTimeMillis() - loc.getTime()) / 1000.0));
                } else { /* ... append N/A as before ... */
                    gpsInfoBuilder.append("🕒 Last Fix: N/A\n");
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    gpsInfoBuilder.append("🛰 Satellites: Waiting for status...\n");
                } else {
                    gpsInfoBuilder.append("🛰 Satellite info requires Android 7.0+\n");
                }

            } catch (SecurityException e) { /* ... Log and append error as before ... */
                Log.e("DebugGPSButton", "Permission error getting location data.", e);
                gpsInfoBuilder.append("\n⚠️ Permission error accessing location data.");
            } catch (Exception e) { /* ... Log and append error as before ... */
                Log.e("DebugGPSButton", "Error getting location data.", e);
                gpsInfoBuilder.append("\n⚠️ Error accessing location data.");
            }
        }

        // --- Show Initial Dialog & Store Reference ---
        Log.d("DebugGPSButton", "Calling showDialog with initial info.");
        final String initialDialogText = gpsInfoBuilder.toString();
        // Store the reference to the dialog shown initially
        gpsStatusDialog = showDialog("GPS Status", initialDialogText);

        // --- Register Callback (if applicable and GPS enabled) ---
        if (isGpsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.d("DebugGPSButton", "Registering GnssStatus Callback to potentially update dialog.");
            final GnssStatus.Callback gnssCallback = new GnssStatus.Callback() {
                private boolean alreadyUnregistered = false; // Renamed flag

                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    // Only update if the dialog we stored is still showing
                    if (gpsStatusDialog == null || !gpsStatusDialog.isShowing()) {
                        Log.d("DebugGPSButton", "Callback fired, but dialog is null or not showing. Unregistering.");
                        // Still try to unregister even if dialog isn't showing
                        if (!alreadyUnregistered && locationManager != null) {
                            try {
                                locationManager.unregisterGnssStatusCallback(this);
                                alreadyUnregistered = true;
                                Log.d("DebugGPSButton", "Unregistered GnssStatus callback (dialog closed).");
                            } catch (Exception e) {
                                Log.e("DebugGPSButton", "Error unregistering GNSS callback (dialog closed)", e);
                            }
                        }
                        return; // Don't proceed if dialog is gone
                    }

                    Log.d("DebugGPSButton", "onSatelliteStatusChanged callback received, dialog is showing.");
                    int count = status.getSatelliteCount();
                    int used = 0;
                    for (int i = 0; i < count; i++) if (status.usedInFix(i)) used++;
                    String satInfo = String.format("🛰 Satellites Used: %d/%d\n", used, count);

                    // Update the dialog message
                    String updatedDialogText = initialDialogText.replace("🛰 Satellites: Waiting for status...\n", satInfo);

                    Log.d("DebugGPSButton", "Updating existing dialog message.");
                    // Update the message of the existing dialog instance
                    gpsStatusDialog.setMessage(updatedDialogText);
                    // Optionally update title if desired
                    // gpsStatusDialog.setTitle("GPS Status (Updated)");

                    // Unregister the callback
                    if (!alreadyUnregistered && locationManager != null) {
                        try {
                            locationManager.unregisterGnssStatusCallback(this);
                            alreadyUnregistered = true; // Mark as unregistered
                            Log.d("DebugGPSButton", "Unregistered GnssStatus callback (after update).");
                        } catch (Exception e) {
                            Log.e("DebugGPSButton", "Error unregistering GNSS callback (after update)", e);
                        }
                    }
                }
            };

            // Register the callback (existing try-catch logic remains the same)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    locationManager.registerGnssStatusCallback(getMainExecutor(), gnssCallback);
                    Log.d("DebugGPSButton", "Registered GnssStatus Callback (using getMainExecutor).");
                } else { /* ... handle pre-R ... */ }
            } catch (SecurityException secEx) { /* ... Log ... */ } catch (
                    Exception regEx) { /* ... Log ... */ }
        }

        Log.d("DebugGPSButton", "showGPSStatus() finished initial setup.");
    }

    private AlertDialog showDialog(String title, String message) { // Changed return type to AlertDialog
        Log.d("DebugGPSButton", "showDialog() called with title: " + title);

        // Dismiss the previous dialog instance, if it's showing
        if (gpsStatusDialog != null && gpsStatusDialog.isShowing()) {
            Log.d("DebugGPSButton", "Dismissing previous gpsStatusDialog.");
            try {
                gpsStatusDialog.dismiss();
            } catch (Exception e) {
                Log.w("DebugGPSButton", "Error dismissing previous dialog", e);
            }
        }
        gpsStatusDialog = null; // Clear the reference

        if (isFinishing() || isDestroyed()) {
            Log.w("DebugGPSButton", "showDialog() aborted: Activity finishing/destroyed.");
            return null; // Return null if not shown
        }

        // Use a final variable for the AlertDialog to be returned
        final AlertDialog[] dialogToShow = {null};

        // Still use runOnUiThread for safety
        runOnUiThread(() -> {
            Log.d("DebugGPSButton", "showDialog() - Running on UI thread.");
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            // Clear the reference when the user dismisses the dialog
                            if (gpsStatusDialog == dialog) {
                                gpsStatusDialog = null;
                            }
                        })
                        .setOnDismissListener(dialog -> {
                            // Also clear reference if dismissed via back press etc.
                            if (gpsStatusDialog == dialog) {
                                gpsStatusDialog = null;
                            }
                        });

                dialogToShow[0] = builder.create(); // Create the dialog
                dialogToShow[0].show(); // Show it
                Log.d("DebugGPSButton", "showDialog() - AlertDialog shown.");

            } catch (Exception dialogEx) {
                Log.e("DebugGPSButton", "Error creating/showing AlertDialog", dialogEx);
            }
        });
        // Return the dialog instance (might be null initially due to runOnUiThread)
        // We will store it globally via the member variable anyway
        return dialogToShow[0]; // This might still be null immediately after call, rely on member var
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG_SYNC, "--------- onServiceConnected START ---------"); // <<< ADD LOG START
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocationTrackingService.LocalBinder binder = (LocationTrackingService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            Log.d(TAG, "Service Bound from MainActivity");
            // *** NEW: Query service state immediately after binding ***
            if (mService != null) {
                boolean serviceIsTracking = mService.isCurrentlyTracking();
                Log.w(TAG_SYNC, "ACTIVITY onServiceConnected: Service says tracking = " + serviceIsTracking + ". Current Activity flag was = " + MainActivity.this.isTrackingActive);
                // Update MainActivity's state based on the service's ground truth
                MainActivity.this.isTrackingActive = serviceIsTracking;
                Log.w(TAG_SYNC, "ACTIVITY onServiceConnected: UPDATED MainActivity.isTrackingActive to = " + MainActivity.this.isTrackingActive);

                // Update button states etc. based on the correct state NOW
                Log.d(TAG_SYNC, "onServiceConnected: Calling uiUpdater.updateStartStopButtonState"); // <<< ADD LOG
                if (uiUpdater != null) {
                    // Pass false for isTrackingActive and "Still" for effectiveMode
                    uiUpdater.updateStartStopButtonState(isTrackingActive, "Still");
                } else {
                    Log.w(TAG, "UiUpdater is null in stopTracking.");
                }                String modeFromPrefsLine1297 = getSharedPreferences("Settings", MODE_PRIVATE).getString(KEY_TRACKING_MODE, "auto"); // Read mode
                if (uiUpdater != null) {
                    uiUpdater.updateUiBasedOnTrackingMode(modeFromPrefsLine1297, isTrackingActive); // Add isTrackingActive
                }
                // Now, trigger the polyline update using the fresh state
                Log.d(TAG_SYNC, "onServiceConnected: Triggering polyline update from service."); // <<< ADD LOG
                updateCurrentPolylineFromService();

            } else {
                Log.e(TAG_SYNC, "onServiceConnected: mService is null after binding!"); // <<< Use TAG_SYNC
                // Handle error case - maybe default isTrackingActive to false?
                MainActivity.this.isTrackingActive = false;
                Log.d(TAG_SYNC, "onServiceConnected: Calling uiUpdater.updateStartStopButtonState (error case)"); // <<< ADD LOG
                if (uiUpdater != null) {
                    // Pass false for isTrackingActive and "Still" for effectiveMode
                    uiUpdater.updateStartStopButtonState(isTrackingActive, "Still");
                } else {
                    Log.w(TAG, "UiUpdater is null in stopTracking.");
                }            }
            Log.d(TAG_SYNC, "--------- onServiceConnected END ---------"); // <<< ADD LOG END
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG_SYNC, "--------- onServiceDisconnected ---------"); // <<< ADD LOG
            mBound = false;
            mService = null; // Clear the service instance
            // Optionally set isTrackingActive to false here, or rely on next binding?
            // MainActivity.this.isTrackingActive = false; // Cautious approach
            // updateStartStopButtonState();
            Log.d(TAG, "Service Unbound from MainActivity"); // Existing log
        }
    };

    private class LocationBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("BroadcastDebug", "MainActivity Receiver onReceive ENTERED.");
            if (intent == null) {
                Log.w("BroadcastDebug", "MainActivity Receiver: Intent is NULL.");
                return;
            }
            final String action = intent.getAction();
            Log.d("BroadcastDebug", "MainActivity Receiver: Received Action = " + action);

            // --- Handle Location Broadcast ---
            if (LocationTrackingService.ACTION_LOCATION_BROADCAST.equals(action)) {
                Log.d("BroadcastDebug", "Handling ACTION_LOCATION_BROADCAST.");

                // 1. Extract Location
                Location location = intent.getParcelableExtra(LocationTrackingService.EXTRA_LOCATION);
                Log.d("BroadcastDebug", "Receiver: Extracted Location object is " + (location == null ? "NULL" : "NOT NULL")); // <-- ADD THIS LOG
                if (location == null) {
                    Log.w("BroadcastDebug", "MainActivity Receiver: Location object is NULL in broadcast. Returning early."); // <-- ADD THIS LOG
                    return; // Exit if no location data
                }

                // 2. Determine Mode and Recording Status
                String modeForIcon = intent.getStringExtra(LocationTrackingService.EXTRA_EFFECTIVE_MODE);
                if (modeForIcon == null) modeForIcon = "Unknown";
                LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                boolean isRecordingActive = intent.getBooleanExtra(LocationTrackingService.EXTRA_IS_RECORDING_ACTIVE, false);
                Log.d("RecordingIndicator", "Received broadcast: isRecordingActive = " + isRecordingActive);

                // 3. Update User Marker
                if (mapManager != null) {
                    mapManager.updateUserLocationMarker(currentLatLng, modeForIcon);
                }

                // 4. Update GPS Indicator
                if (uiUpdater != null) {
                    uiUpdater.updateGPSIndicatorFromLocation(location);
                }

                // 5. Update Current Polyline & Status (only if tracking is generally active)
                Log.d("BroadcastDebug", "Receiver: Checking MainActivity.this.isTrackingActive = " + MainActivity.this.isTrackingActive); // <-- ADD THIS LOG
                if (MainActivity.this.isTrackingActive) {
                    // Determine the mode to display, respecting override
                    final String displayMode = (MainActivity.this.overrideMode != null) ? MainActivity.this.overrideMode : modeForIcon;

                    // Update Polyline Color (Always based on mode)
                    updateCurrentPolylineLayerColor(displayMode);

                    // Control Blinking Animation based on Recording State
                    if (currentPolylineAnimator != null) {
                        // Check if the target is actually set before trying to blink
                        if (currentPolylineAnimator.getTargetLayerId() != null) { // <<< ADD CHECK
                            if (isRecordingActive) {
                                Log.i("BroadcastDebug", "Calling startBlinking() from BroadcastReceiver (isRecordingActive=true)"); // <-- ADD LOG
                                currentPolylineAnimator.startBlinking();
                            } else {
                                Log.d("RecordingIndicator", "Stopping polyline blinking (Recording Inactive, Target Set)");
                                currentPolylineAnimator.stopBlinking();
                            }
                        } else {
                            // Log if the target wasn't set, preventing the blinking attempt
                            Log.w("RecordingIndicator", "Cannot control blinking: Animator targetLayerId is null.");
                            // Maybe try setting the target again? Or wait for initializeCurrentPolyline?
                            // initializeCurrentPolyline(displayMode); // Careful: might reset polyline unnecessarily
                        }
                    } else {
                        Log.w("RecordingIndicator", "currentPolylineAnimator is null, cannot control blinking.");
                    }

                    // Update Status Label (Add [REC] prefix if recording)
                    String statusText;
                    if (isRecordingActive) {
                        statusText = "[REC] Tracking: " + displayMode;
                    } else {
                        statusText = "Tracking: Starting..."; // Or "Tracking: Waiting..."
                    }


                    if (uiUpdater != null) {
                        Log.d("BroadcastDebug", "Receiver: Updating icon drawable for mode: " + displayMode);
                        uiUpdater.updateTransportModeIcon(displayMode); // Set the correct image

                        Log.d("BroadcastDebug", "Receiver: Updating icon visibility/animation for mode: " + displayMode);
                        // Pass true for isTrackingActive because we are inside this check
                        uiUpdater.updateStartStopButtonState(true, displayMode); // Update visibility/animation
                    } else {
                        Log.w(TAG, "UiUpdater is null in ACTION_LOCATION_BROADCAST receiver!");
                    }

                    if (historicalVisibilitySetting == SettingsActivity.MODE_HIDE_UNRELATED) {
                        Log.d("BroadcastDebug", "Receiver: Calling updateHistoricalPolylinesVisibility for HIDE_UNRELATED check. Mode: " + displayMode); // <-- ADD LOG
                        updateHistoricalJourneyVisibility(displayMode); // Update based on current mode
                    }

                    // Update Polyline Source (only add point if recording)
                    double lat = intent.getDoubleExtra(LocationTrackingService.EXTRA_NEW_GEOPOINT_LAT, LocationTrackingService.INVALID_LAT_LON);
                    double lon = intent.getDoubleExtra(LocationTrackingService.EXTRA_NEW_GEOPOINT_LON, LocationTrackingService.INVALID_LAT_LON);
                    Log.d("BroadcastDebug", "Receiver (Inside isTrackingActive check): Extracted Lat=" + lat + ", Lon=" + lon + ", isRecordingActive=" + isRecordingActive);

                    if (lat != LocationTrackingService.INVALID_LAT_LON && lon != LocationTrackingService.INVALID_LAT_LON) {
                        if (isRecordingActive) { // <<< Check isRecordingActive HERE
                            LatLng newPoint = new LatLng(lat, lon);
                            currentTrackLatLngs.add(newPoint); // Add to local list
                            Log.i("BroadcastDebug", ">>> POINT ADDED to MainActivity list (Inside isTrackingActive check). New Size: " + currentTrackLatLngs.size());

                            updateCurrentPolylineSource();     // Update the map source
                        } else {
                            Log.w("BroadcastDebug", "Point received but isRecordingActive is false. Not adding to MainActivity list.");
                        }
                    }
                } else {
                    Log.w("BroadcastDebug", "Received invalid Lat/Lon (Inside isTrackingActive check). Not adding point.");

                    // Tracking stopped - ensure animation is stopped (also handled in ACTION_TRACKING_STATE_CHANGED)
                    if (currentPolylineAnimator != null) {
                        currentPolylineAnimator.stopBlinking();
                    }
                }

                // 6. Always Invalidate Map View (on UI thread) after processing location
                // Ensures marker and polyline updates are shown
                runOnUiThread(() -> {
                    if (mapView != null) {
                        mapView.invalidate();
                    }
                });

            }
            // --- Handle Tracking State Changes ---
            else if (LocationTrackingService.ACTION_TRACKING_STATE_CHANGED.equals(action)) {
                Log.w("StateSyncDebug", "ACTIVITY: Received ACTION_TRACKING_STATE_CHANGED.");
                boolean serviceIsTracking = intent.getBooleanExtra(LocationTrackingService.EXTRA_IS_TRACKING, false);
                Log.w("StateSyncDebug", "ACTIVITY: Service says tracking = " + serviceIsTracking + ". Current Activity isTrackingActive = " + MainActivity.this.isTrackingActive);

                SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
                String currentMode = prefs.getString(KEY_TRACKING_MODE, MODE_AUTO);

                // Only update if the state actually changed in MainActivity
                if (MainActivity.this.isTrackingActive != serviceIsTracking) {
                    MainActivity.this.isTrackingActive = serviceIsTracking;
                    Log.w("StateSyncDebug", "ACTIVITY: UPDATED MainActivity.isTrackingActive to = " + MainActivity.this.isTrackingActive);

                    // Update button states etc.
                    if (uiUpdater != null) {
                        // Pass the *new* tracking state and a default mode ("Still" is safe)
                        // The specific mode icon/text will be updated by location broadcasts if tracking is active
                        uiUpdater.updateStartStopButtonState(MainActivity.this.isTrackingActive, "Still");
                        uiUpdater.updateUiBasedOnTrackingMode(currentMode, MainActivity.this.isTrackingActive);
                    }

                    if (serviceIsTracking) {
                        Log.d("MainActivityReceiver", "Tracking started broadcast received.");
                        // Visual setup for current polyline happens in startTracking() or location broadcast
                    } else {
                        Log.d("MainActivityReceiver", "Tracking stopped broadcast received.");
                        // Ensure animation stops and polyline is cleared
                        if (currentPolylineAnimator != null) {
                            Log.d("RecordingIndicator", "Stopping blinking due to tracking state change (Stopped).");
                            currentPolylineAnimator.stopBlinking();
                        }
                        currentTrackLatLngs.clear(); // Clear points when tracking stops
                        updateCurrentPolylineSource(); // Update map source to be empty
                        // Update status label if needed (e.g., show "Idle")
                    }
                } else {
                    Log.d("MainActivityReceiver", "Skipping state update - Activity state already matches service state (" + MainActivity.this.isTrackingActive + ")");
                }

            }
            // --- Handle Detected Activity (for UI feedback when inactive) ---
            else if (LocationTrackingService.ACTION_ACTIVITY_DETECTED.equals(action)) {
                updateHistoricalJourneyVisibility(null);
                String detectedMode = intent.getStringExtra(LocationTrackingService.EXTRA_DETECTED_ACTIVITY_STRING);
                if (detectedMode == null) detectedMode = "Unknown";
                Log.d("MainActivityReceiver", "Received ACTION_ACTIVITY_DETECTED: Detected Mode = " + detectedMode);
                updateUiForDetectedActivity(detectedMode); // Update UI based on detection (only when inactive)

            }
            // --- Handle GPS Status / Permissions ---
            else if (LocationTrackingService.ACTION_GPS_DISABLED.equals(action)) {
                Log.w(TAG, "Received GPS Disabled broadcast.");
                if (uiUpdater != null) {
                    uiUpdater.updateGPSIndicator(0);
                }
                Toast.makeText(MainActivity.this, "GPS Disabled", Toast.LENGTH_SHORT).show();

            } else if (LocationTrackingService.ACTION_LOCATION_PERMISSION_ERROR.equals(action)) {
                Log.e(TAG, "Received Location Permission Error broadcast.");
                if (uiUpdater != null) {
                    uiUpdater.updateGPSIndicator(0);
                }
                Toast.makeText(MainActivity.this, "Location Permission Error", Toast.LENGTH_LONG).show();
            }
            // --- Handle Full Activity List (for popup) ---
            else if (MainActivity.ACTION_ALL_ACTIVITIES_UPDATE.equals(action)) {
                Log.d("BroadcastDebug", "Receiver: Handling ACTION_ALL_ACTIVITIES_UPDATE");
                ArrayList<DetectedActivity> activities = intent.getParcelableArrayListExtra(EXTRA_ALL_ACTIVITIES);
                if (activities != null) {
                    Log.d(TAG, "Received " + activities.size() + " detected activities with confidences.");
                    synchronized (lastDetectedActivities) { // Synchronize access if needed
                        lastDetectedActivities = activities;
                    }
                } else {
                    Log.w(TAG, "Received ACTION_ALL_ACTIVITIES_UPDATE but the extra was null.");
                }
            }

        } // End onReceive
    } // End LocationBroadcastReceiver


    private static final String TAG_UI = "MainActivityUI";

    private void updateUiForDetectedActivity(String detectedMode) {
        try {
            // Log entry with the detected mode
            Log.d(TAG_UI, "updateUiForDetectedActivity: Received detectedMode='" + detectedMode + "', isTrackingActive=" + isTrackingActive);

            // This method logic should only affect UI when tracking is INACTIVE
            if (isTrackingActive) {
                Log.d(TAG_UI, "updateUiForDetectedActivity: Exiting because tracking is active.");
                return; // Do nothing if tracking is already active
            }

            // --- Determine if the detected activity is one we should show an icon for ---
            boolean isRecordableActivity = false;
            if (detectedMode != null) {
                switch (detectedMode) {
                    case "Walking":
                    case "Bicycling":
                    case "In Vehicle":
                        isRecordableActivity = true;
                        break;
                    // Still, Unknown, Tilting, etc. will leave isRecordableActivity = false
                    default:
                        isRecordableActivity = false;
                        break;
                }
            }
            Log.d(TAG_UI, "updateUiForDetectedActivity: isRecordableActivity = " + isRecordableActivity);


            // --- Update Icons (Marker and Top Icon) ---
            if (transportModeIcon == null || mapManager == null ) { // Check mapManager too
                Log.w(TAG_UI, "updateUiForDetectedActivity: transportModeIcon, MapManager or userMarker is null, cannot update icons.");
                return;
            }

            if (isRecordableActivity) {
                // Set the specific icon for the detected mode
                updateTransportModeIcon(detectedMode); // Use existing helper to set BOTH top icon and marker icon
                transportModeIcon.setVisibility(View.VISIBLE); // Make top icon visible
                transportModeIcon.setClickable(false); // Make sure it's not clickable when inactive
                if (transportModeIcon.getAnimation() != null)
                    transportModeIcon.clearAnimation(); // Stop animation if any
                transportModeIcon.setBackgroundResource(R.drawable.circle_button); // Use inactive background

                // Optional: Explicitly set marker if updateTransportModeIcon doesn't do it
                // Drawable markerIcon = ContextCompat.getDrawable(this, getDrawableResourceIdForMode(detectedMode));
                // if (markerIcon != null) userMarker.setIcon(markerIcon);

                Log.d(TAG_UI, "Set Recordable Activity Icons (Inactive Tracking): Mode=" + detectedMode);
                // TODO: Update MapLibre SymbolLayer's iconImage property to the default icon ID

            }

        } catch (Exception e) { // Catch unexpected errors during UI update
            Log.e(TAG_UI, "Error in updateUiForDetectedActivity", e);
        }
        Log.d(TAG_UI, "updateUiForDetectedActivity FINISHED."); // Use Log.d for finish message
    }


    // Ensure updateTransportModeIcon exists and handles the icons correctly
    private void updateTransportModeIcon(String transportMode) {
        int iconResId = getDrawableResourceIdForMode(transportMode); // Use helper
        if (transportModeIcon != null) {
            transportModeIcon.setImageResource(iconResId);
        }
        // TODO: The logic to update the MapLibre user marker icon should be in MapManager.updateUserLocationMarker
    }

    private void startTracking() {
        // Check permissions first (although should be granted by now)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission needed to start tracking.", Toast.LENGTH_SHORT).show();
            if (permissionHelper != null) {
                permissionHelper.checkAndRequestBasePermissions(); // Ask again via helper
            }
            return;
        }

        String modeFromPrefs = getSharedPreferences("Settings", MODE_PRIVATE).getString(KEY_TRACKING_MODE, MODE_AUTO);
        String initialMode = MODE_MANUAL.equals(modeFromPrefs) ? overrideMode : "Unknown"; // Use override if manual
        if (initialMode == null) initialMode = "Unknown"; // Handle case where override hasn't been set yet

        initializeCurrentPolyline(initialMode);
        updateCurrentPolylineStyle(initialMode);
        updateHistoricalPolylinesVisibility(initialMode); // <-- CALL HERE

        if (currentPolylineAnimator != null ) {
            currentPolylineAnimator.startBlinking();
        }


        Log.i(TAG, "Starting Tracking...");
        isTrackingActive = true;
        updateHistoricalJourneyVisibility(initialMode);
        if (uiUpdater != null) {
            // Pass false for isTrackingActive and "Still" for effectiveMode
            uiUpdater.updateStartStopButtonState(isTrackingActive, "Still");
        } else {
            Log.w(TAG, "UiUpdater is null in stopTracking.");
        }
        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        String currentMode = prefs.getString(KEY_TRACKING_MODE, MODE_AUTO);
        if (MODE_MANUAL.equals(currentMode)) {
            // Apply default override BEFORE setting label
            applyOverrideMode("Walking"); // Apply default and broadcast to service
        } else {
            clearOverrideMode();
        }
        // <<< ADD: To store last mode when tracking
        String lastKnownModeForLabel = MODE_MANUAL.equals(currentMode) ? "Walking" : "Auto"; // Store initial mode

        // ... existing code to start service, initialize polyline, show Toast ...

        if (uiUpdater != null) {
            // *** ADD isTrackingActive ***
            uiUpdater.updateUiBasedOnTrackingMode(currentMode, MainActivity.this.isTrackingActive);
        }
        // Start the foreground service
        startLocationService(); // You already have this helper method

        // Initialize a new visual polyline for the current track
        initializeCurrentPolyline("Unknown"); // Creates the new this.currentPolyline object


        // Update animator (Keep this, but CurrentPolylineAnimator needs rewrite for MapLibre Layers)
        // if (currentPolylineAnimator != null && this.currentPolyline != null) { // Need MapLibre equivalent check
        if (currentPolylineAnimator != null ) { // Temporary check
            // currentPolylineAnimator.setTargetPolyline(this.currentPolyline); // Pass MapLibre Layer/Source ID?
            currentPolylineAnimator.startBlinking();
        } else {
            Log.w(TAG,"Cannot start animator or currentPolyline is null");
        }

        currentTrackMapPoints.clear();
        updateCurrentPolylineSource(); // Update source with empty line/point
        // Set initial style based on starting mode
        updateCurrentPolylineStyle(initialMode);

        // TODO: Show recording indicator (Item #12)
        Toast.makeText(this, "Tracking Started", Toast.LENGTH_SHORT).show(); // Simple feedback

        prefs = getSharedPreferences("Settings", MODE_PRIVATE); // Re-assign existing variable
        currentMode = prefs.getString(KEY_TRACKING_MODE, MODE_AUTO); // Re-assign existing variable
        if (uiUpdater != null) {
            // *** ADD isTrackingActive ***
            uiUpdater.updateUiBasedOnTrackingMode(currentMode, MainActivity.this.isTrackingActive);
        }
        initializeCurrentPolyline(initialMode); // Pass the determined initial mode
    }

    private void stopTracking() {
        clearOverrideMode();
        Log.i(TAG, "Stopping Tracking...");
        isTrackingActive = false;
        updateHistoricalJourneyVisibility(null);
        if (uiUpdater != null) {
            // Pass false for isTrackingActive and "Still" for effectiveMode
            uiUpdater.updateStartStopButtonState(isTrackingActive, "Still");
        } else {
            Log.w(TAG, "UiUpdater is null in stopTracking.");
        }
        // Stop the service
        Intent serviceIntent = new Intent(this, LocationTrackingService.class);
        stopService(serviceIntent); // Stop the service
        Log.d(TAG, "Attempted to stop LocationTrackingService.");



        // Stop animator (Keep, but needs adaptation)
        if (currentPolylineAnimator != null) {
            currentPolylineAnimator.stopBlinking();
        }
        Log.d(TAG, "stopTracking: Calling updateHistoricalPolylinesVisibility with mode: null");
        updateHistoricalPolylinesVisibility(null); // <-- CALL HERE (passing null indicates tracking stopped)
        Toast.makeText(this, "Tracking Stopped", Toast.LENGTH_SHORT).show();

        // Optional: Reload all polylines to show the just-finished one immediately
        // loadAllPolylineData();

        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        String currentMode = prefs.getString(KEY_TRACKING_MODE, MODE_AUTO);
        if (uiUpdater != null) {
            // *** ADD isTrackingActive ***
            uiUpdater.updateUiBasedOnTrackingMode(currentMode, MainActivity.this.isTrackingActive);
        }
    }


    private void unregisterReceiver() {
        if (locationReceiver != null) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver);
                Log.d(TAG, "Location BroadcastReceiver Unregistered");
            } catch (IllegalArgumentException e) {
                Log.w(TAG,"Receiver already unregistered? " + e.getMessage());
            }
        }
    }

    private void setupSettingsLauncher() {
        settingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "SettingsActivity Result Received: Code=" + result.getResultCode());
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        boolean tracksCleared = data != null && data.getBooleanExtra("tracksCleared", false);
                        boolean styleChanged = true; // Assume style might have changed
                        boolean forceRematch = data != null && data.getBooleanExtra("force_rematch_on_next_load", false);

                        if (tracksCleared) {
                            Log.i(TAG, "Settings indicate tracks were cleared. Reloading all journeys.");
                            isJourneyDataLoaded = false; // Reset flag
                            if (journeyManager != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { // NEW
                                journeyManager.loadAllPolylineData(); // Trigger reload
                            }
                        } else if (styleChanged && maplibreMap != null) {
                            // Re-apply the style from settings
                            Log.i(TAG, "Settings returned, re-applying map style.");
                            applyMapStyleFromSettings(); // New helper needed
                        } else {
                            Log.d(TAG, "SettingsActivity returned OK, but no major changes detected requiring map reload/restyle.");
                        }
                        // Check if force rematch flag was set
                        if (forceRematch && journeyManager != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { // NEW
                            Log.i(TAG, "Force rematch flag detected, reloading journeys.");
                            isJourneyDataLoaded = false; // Allow reload
                            journeyManager.loadAllPolylineData();
                        }
                    } else {
                        Log.d(TAG, "SettingsActivity returned result Canceled or other code: " + result.getResultCode());
                    }
                    // Re-check orientation lock setting after returning
                    SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
                    isOrientationLocked = prefs.getBoolean(SettingsActivity.KEY_LOCK_ORIENTATION, true);
                });
    }

    private void applyMapStyleFromSettings() {
        if (MainActivity.this.maplibreMap == null) {
            Log.w(TAG, "applyMapStyleFromSettings: maplibreMap is null, cannot apply style.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        String styleIdentifier = prefs.getString(SettingsActivity.KEY_MAP_STYLE_IDENTIFIER, SettingsActivity.DEFAULT_MAP_STYLE);
        Log.d(TAG, "applyMapStyleFromSettings: Applying style identifier: " + styleIdentifier);

        Style.Builder styleBuilder = null;
        String styleUrl = null; // Declare styleUrl here

        // --- Determine Style URL ---
        if (styleIdentifier.contains("://") || styleIdentifier.endsWith(".json")) {
            styleUrl = styleIdentifier;
            Log.d(TAG, "Using direct style URL: " + styleUrl);
        } else {
            // *** Use MapManager's helper to get URL ***
            styleUrl = MapManager.getStyleUrl(styleIdentifier); // Look up URL
            if (styleUrl == null) {
                Log.e(TAG, "Unknown predefined style name '" + styleIdentifier + "'. Defaulting.");
                // *** Use MapManager's default ***
                styleUrl = MapManager.getStyleUrl(MapManager.DEFAULT_STYLE_IDENTIFIER);
                if (styleUrl == null) {
                    Log.e(TAG, "Default style 'OSM Bright' URL is also missing! Cannot set style.");
                    Toast.makeText(this, "Error: Default map style not found.", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                Log.d(TAG, "Using predefined style key '" + styleIdentifier + "' URL: " + styleUrl);
            }
        }

        // --- Create Style Builder ---
        styleBuilder = new Style.Builder().fromUri(styleUrl); // Create builder from the determined URL

        // --- Define OnStyleLoaded Listener ---
        // Listener definition remains the same, using MainActivity.this.maplibreMap inside
        Style.OnStyleLoaded onStyleLoadedListener = new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                Log.i(TAG, ">>> onStyleLoaded (from Settings): Style loaded successfully: " + style.getUri());

                if (MainActivity.this.maplibreMap == null) {
                    Log.e(TAG, "onStyleLoaded (from Settings): maplibreMap member is null!");
                    return;
                }

                Log.d(TAG, "onStyleLoaded (from Settings): Setting up map-dependent features...");
                setupMapDependentFeatures(style); // Re-setup layers, sources, etc.

                Log.d(TAG, "onStyleLoaded (from Settings): Adding map click listener...");
                addMapClickListener(MainActivity.this.maplibreMap); // Use class member

                Log.d(TAG, "onStyleLoaded (from Settings): Applying gesture settings...");
                SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
                isOrientationLocked = prefs.getBoolean(SettingsActivity.KEY_LOCK_ORIENTATION, true);
                MainActivity.this.maplibreMap.getUiSettings().setRotateGesturesEnabled(!isOrientationLocked); // Use class member
                Log.d(TAG_ROTATION, "onStyleLoaded (from Settings): Applied rotate gesture setting: enabled=" + !isOrientationLocked);

                // NOTE: We probably DON'T need to check permissions or reload journeys here,
                // as this method is called *after* returning from settings, where those
                // checks might have already happened in onResume or the ActivityResultLauncher.
                // Reloading journeys on every style change might be unnecessary.
            }
        };

        // --- Set the Style using the 'maplibreMap' MEMBER variable and 'styleUrl' ---
        Log.d(TAG, "applyMapStyleFromSettings: Calling maplibreMap.setStyle with URL: " + styleUrl);
        // *** CORRECTED LINE ***
        MainActivity.this.maplibreMap.setStyle(styleBuilder, onStyleLoadedListener);

    } // End of applyMapStyleFromSettings

    private void setupRootViewInsets() {
        View rootView = findViewById(R.id.rootFrameLayout);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBarInsets.left, systemBarInsets.top, systemBarInsets.right, systemBarInsets.bottom);
                Log.d(TAG, "Applied system bar insets as padding. Bottom: " + systemBarInsets.bottom);
                return windowInsets;
            });
        } else {
            Log.e(TAG, "Root layout (rootFrameLayout) not found!");
        }
    }










    // Helper to get drawable resource ID based on mode string
    // Ensure this exists or adapt updateTransportModeIcon to handle marker too
    private int getDrawableResourceIdForMode(String mode) {
        switch (mode) {
            case "Walking":
                return R.drawable.ic_walking;
            case "Bicycling":
                return R.drawable.ic_directions_bike;
            case "In Vehicle":
                return R.drawable.ic_directions_in_vehicle;
            default:
                return R.drawable.ic_man_still; // Fallback
        }
    }



    // Helper to load prefs and update UI initially and onResume
    private void loadAndApplyInitialUiState() {
        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        // *** Use the variable declared above ***
        String trackingMode = prefs.getString(KEY_TRACKING_MODE, MODE_AUTO); // Default to Auto
        Log.d(TAG_SYNC, "loadAndApplyInitialUiState: Applying UI for Mode=" + trackingMode + ", Activity's isTrackingActive=" + this.isTrackingActive);

        Log.d(TAG, "loadAndApplyInitialUiState: Tracking Mode loaded: " + trackingMode);
        if (uiUpdater != null) {
            // *** FIX: Use trackingMode and add isTrackingActive ***
            uiUpdater.updateUiBasedOnTrackingMode(trackingMode, MainActivity.this.isTrackingActive);
        }
    }



    // Define the listener logic separately to re-attach it
// Make this a member variable if accessed elsewhere, or keep it local if only used in onCreate's setOnCheckedChangeListener call
    private CompoundButton.OnCheckedChangeListener switchListener = (buttonView, isChecked) -> {
        // isChecked = true means Manual is ON
        String newMode = isChecked ? MODE_MANUAL : MODE_AUTO;
        Log.d(TAG, "Tracking mode switch toggled by user. New mode: " + newMode);

        // Save the new preference
        saveTrackingModePreference(newMode);

        if (uiUpdater != null) {
            // Pass the NEW mode and the CURRENT tracking state
            // *** ADD isTrackingActive ***
            uiUpdater.updateUiBasedOnTrackingMode(newMode, isTrackingActive); // Delegate UI update
        }

        // If switching TO AUTO mode while tracking was manually active, stop it
        if (!isChecked && isTrackingActive) { // !isChecked means Auto mode selected
            Log.i(TAG, "Switched to Auto mode while tracking was active. Stopping manual track.");
            stopTracking(); // Call your existing stopTracking method
        }
    };

    // Helper method to save preference
    private void saveTrackingModePreference(String mode) {
        SharedPreferences preferences = getSharedPreferences("Settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_TRACKING_MODE, mode);
        editor.apply();
        Log.d(TAG, "Saved Tracking Mode preference: " + mode);
    }


    // Helper to apply and broadcast override
    private void applyOverrideMode(String selectedMode) {
        Log.d(TAG, "applyOverrideMode: Applying mode '" + selectedMode + "'");
        this.overrideMode = selectedMode;


        // Initialize the *concept* of a new segment (method needs rewrite)
        Log.d(TAG, "applyOverrideMode: Initializing new visual polyline for mode: " + this.overrideMode);
        initializeCurrentPolyline(this.overrideMode); // Keep call, method needs internal rewrite


        // Update animator (Keep, needs adaptation)
        if (currentPolylineAnimator != null) {
            // currentPolylineAnimator.setTargetPolyline(this.currentPolyline); // Adapt target
            if (isTrackingActive) {
                currentPolylineAnimator.startBlinking();
            } else {
                currentPolylineAnimator.stopBlinking();
            }
        }


        // Update the top icon immediately
        uiUpdater.updateTransportModeIcon(this.overrideMode);

        Log.w(TAG,"applyOverrideMode: MapLibre Source/Layer setup for current polyline pending.");


        // Send broadcast to the service AFTER handling visual split
        Intent overrideIntent = new Intent(ACTION_MODE_OVERRIDE);
        overrideIntent.putExtra(EXTRA_OVERRIDE_MODE, this.overrideMode);
        LocalBroadcastManager.getInstance(this).sendBroadcast(overrideIntent);
        Log.d(TAG, "applyOverrideMode: Sent broadcast. Mode=" + this.overrideMode);
    }

    // Helper to clear override and broadcast
    private void clearOverrideMode() {
        Log.d(TAG, "clearOverrideMode: Clearing override."); // Add log
        this.overrideMode = null; // Clear local override

        // Initialize the *concept* of a new segment
        Log.d(TAG, "clearOverrideMode: Initializing new visual polyline for mode: Unknown");
        initializeCurrentPolyline("Unknown"); // Keep call, method needs internal rewrite

        // --- Add Placeholder ---
        // TODO: Ensure MapLibre Source/Layer for the current polyline exists and is styled for 'Unknown'.
        Log.w(TAG,"clearOverrideMode: MapLibre Source/Layer setup for current polyline pending.");


        // Update animator (Keep, needs adaptation)
        if (currentPolylineAnimator != null) {
            // currentPolylineAnimator.setTargetPolyline(this.currentPolyline); // Adapt target
            if (isTrackingActive) {
                currentPolylineAnimator.startBlinking();
            } else {
                currentPolylineAnimator.stopBlinking();
            }
        }

        uiUpdater.updateTransportModeIcon(null); // Show default/no icon
        // Let the next location update reset the marker icon based on auto-detect

        // Send broadcast to service AFTER visual split
        Intent overrideIntent = new Intent(ACTION_MODE_OVERRIDE);
        overrideIntent.putExtra(EXTRA_OVERRIDE_MODE, (String) null); // Send null
        LocalBroadcastManager.getInstance(this).sendBroadcast(overrideIntent);
        Log.d(TAG, "clearOverrideMode: Sent null broadcast.");
    }

// Add a helper method for initializing the visual polyline (if not already similar)
// This ensures the new polyline gets the correct initial settings.
// Make sure this method *replaces* the old this.currentPolyline with a new object.


    private void showPopupMenu(View anchorView) {
        Log.d(TAG, "showPopupMenu method called."); // Log to confirm execution
        PopupMenu popup = new PopupMenu(this, anchorView); // Anchor to the clicked icon (the ImageView)

        try {
            // *** Get Mode from SharedPreferences (More Reliable) ***
            SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
            String currentMode = prefs.getString(KEY_TRACKING_MODE, MODE_AUTO); // Default to Auto
            boolean isManualMode = MODE_MANUAL.equals(currentMode);
            // *** Log the determined mode ***
            Log.d(TAG, "showPopupMenu: Determined mode from Prefs: " + currentMode + ", isManualMode = " + isManualMode);
            // Also log switch state for comparison (optional debug)
            if (trackingModeSwitch != null) {
                Log.d(TAG, "showPopupMenu: Current switch state isChecked = " + trackingModeSwitch.isChecked());
            } else {
                Log.w(TAG, "showPopupMenu: trackingModeSwitch is null when trying to log state.");
            }

            // Inflate the menu resource
            popup.getMenuInflater().inflate(R.menu.transport_mode_menu, popup.getMenu());

            // Find the "Use Automatic" menu item
            MenuItem autoModeItem = popup.getMenu().findItem(R.id.menu_mode_auto);

            if (autoModeItem != null) {
                // Set visibility based on mode read from SharedPreferences
                autoModeItem.setVisible(!isManualMode);
                // Log the action taken
                Log.d(TAG, "Setting 'Use Automatic' menu item visibility to: " + !isManualMode);
            } else {
                Log.e(TAG, "Could not find menu item R.id.menu_mode_auto");
            }

            // Set a listener to handle clicks on menu items
            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.menu_mode_walking) {
                    Log.d(TAG, "PopupMenu selected: Walking");
                    applyOverrideMode("Walking"); // Apply and broadcast override
                    return true;
                } else if (itemId == R.id.menu_mode_bicycling) {
                    Log.d(TAG, "PopupMenu selected: Bicycling");
                    applyOverrideMode("Bicycling");
                    return true;
                } else if (itemId == R.id.menu_mode_in_vehicle) {
                    Log.d(TAG, "PopupMenu selected: In Vehicle");
                    applyOverrideMode("In Vehicle");
                    return true;
                } else if (itemId == R.id.menu_mode_auto) {
                    Log.d(TAG, "PopupMenu selected: Use Automatic");
                    clearOverrideMode(); // Clear override and broadcast null
                    return true;
                } else {
                    return false;
                }
            });
            popup.show(); // Display the popup menu
        } catch (Exception e) {
            Log.e(TAG, "Error showing popup menu. Check R.menu.transport_mode_menu exists.", e);
            Toast.makeText(this, "Error showing mode options", Toast.LENGTH_SHORT).show();
        }
    }




    // Helper method for fallback to raw points display
    private void displayRawJourneyFallback(List<PolylinePoint> rawPoints, int journeyIndex, JourneyDetails journeyDetails) {
        Log.w(TAG, "displayRawJourneyFallback: Displaying raw points as a SINGLE polyline for journey index: " + journeyIndex);
        if (rawPoints == null || rawPoints.isEmpty()) {
            Log.e(TAG, "displayRawJourneyFallback: Cannot display, rawPoints are null or empty for index " + journeyIndex);
            // Optionally clear any existing polyline for this index
            clearJourneySegmentsFromMap(journeyIndex); // Ensure cleanup if needed (assumes this helper exists)
            return;
        }

        List<LatLng> rawLatLngs = getGeoPointsFromPolylinePoints(rawPoints); // Use LatLng
        mainThreadHandler.post(() -> {
            updatePolylineOnMap(journeyIndex, rawLatLngs, journeyDetails); // Pass LatLng
        });
    }

    /**
     * Decodes an encoded polyline string (polyline6 format used by Valhalla).
     * NOTE: This is a placeholder - you need to implement the actual decoding logic.
     * @param encodedShape The encoded polyline string.
     * @return A list of GeoPoint objects, or null/empty list on error.
     */
    /**
     * Decodes an encoded polyline string (polyline6 format used by Valhalla).
     * Based on the standard Google Polyline algorithm but adapted for 6 degrees of precision.
     *
     * @param encodedShape The encoded polyline6 string.
     * @return A list of GeoPoint objects, or an empty list on error/empty input.
     */
    /**
     * Decodes an encoded polyline string (polyline6 format used by Valhalla).
     * Based on the standard Google Polyline algorithm but adapted for 6 degrees of precision.
     *
     * @param encodedShape The encoded polyline6 string.
     * @return A list of LatLng objects, or an empty list on error/empty input.
     */
    private List<LatLng> decodeValhallaPolyline(String encodedShape) {
        // Log the beginning of the attempt, showing only a snippet of the potentially long string
        Log.d(TAG, "Decoding polyline6: " + (encodedShape != null && !encodedShape.isEmpty() ? encodedShape.substring(0, Math.min(encodedShape.length(), 50)) + "..." : "null or empty"));

        List<LatLng> poly = new ArrayList<>();
        if (encodedShape == null || encodedShape.isEmpty()) {
            Log.w(TAG, "decodeValhallaPolyline: Input string is null or empty.");
            return poly; // Return empty list
        }

        int index = 0, len = encodedShape.length();
        int lat = 0, lng = 0;
        double factor = 1E6; // Precision factor for polyline6

        try {
            while (index < len) {
                int b, shift = 0, result = 0;
                // Decode latitude
                do {
                    if (index >= len) { // Bounds check
                        throw new IllegalArgumentException("Trying to read past end of string during lat decoding at index " + index);
                    }
                    b = encodedShape.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lat += dlat;

                shift = 0;
                result = 0;
                // Decode longitude
                do {
                    if (index >= len) { // Bounds check
                        throw new IllegalArgumentException("Trying to read past end of string during lon decoding at index " + index);
                    }
                    b = encodedShape.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lng += dlng;

                // Create LatLng with correct precision
                LatLng p = new LatLng(((double) lat / factor), ((double) lng / factor));
                poly.add(p);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error decoding polyline6 at index " + index, e);
            // Return whatever was successfully decoded before the error, or an empty list
            // Returning empty might be safer to avoid partially decoded incorrect paths.
            return new ArrayList<>();
        }

        Log.d(TAG, "Successfully decoded " + poly.size() + " points from polyline6 string.");
        return poly;
    }



    /**
     * Creates or replaces a Polyline overlay on the map for a specific journey index.
     * Ensures only one polyline represents the journey visually.
     *
     * @param journeyIndex The index of the journey.
     * @param points       The list of GeoPoints (matched or raw) to display for the entire journey.
     * @param details      The JourneyDetails, used for determining color and other metadata.
     */
    // Inside MainActivity.java

    private void updatePolylineOnMap(int journeyIndex, List<LatLng> points, JourneyDetails details) {
        final String TAG_LOAD = "JourneyDisplay";
        Log.d(TAG_LOAD, "updatePolylineOnMap: Attempting update for index " + journeyIndex + " with " + (points != null ? points.size() : "null") + " points.");

        if (maplibreMap == null) {
            Log.w(TAG_LOAD, "updatePolylineOnMap: MapLibreMap is null.");
            return;
        }

        // Use getStyle {} lambda for safe style modification
        maplibreMap.getStyle(style -> { // <<< START getStyle lambda
            if (points == null || points.size() < 2) {
                Log.w(TAG_LOAD, "updatePolylineOnMap (in callback): Map ready, but points null or < 2. Cannot draw for index " + journeyIndex);
                // Still attempt to clear any existing layer for this index inside the callback
                clearJourneySegmentsFromMapInternal(style, journeyIndex); // Call internal helper
                return;
            }

            String sourceId = "historical-journey-source-" + journeyIndex;
            String layerId = "historical-journey-layer-" + journeyIndex;

            try {
                List<Point> mapboxPoints = new ArrayList<>(points.size());
                for (LatLng latLng : points) {
                    mapboxPoints.add(Point.fromLngLat(latLng.getLongitude(), latLng.getLatitude()));
                }
                LineString lineString = LineString.fromLngLats(mapboxPoints);
                Feature lineFeature = Feature.fromGeometry(lineString);

                GeoJsonSource geoJsonSource = style.getSourceAs(sourceId);
                if (geoJsonSource == null) {
                    geoJsonSource = new GeoJsonSource(sourceId);
                    style.addSource(geoJsonSource); // Add source inside lambda
                    Log.d(TAG_LOAD, "Added new source: " + sourceId);
                    geoJsonSource.setGeoJson(lineFeature);
                } else {
                    geoJsonSource.setGeoJson(lineFeature);
                    Log.d(TAG_LOAD, "Updated existing source: " + sourceId);
                }

                LineLayer lineLayer = style.getLayerAs(layerId);
                int journeyColor = getColorForTransport(details != null ? details.getDominantMode() : "Unknown");
                float journeyWidth = (float) polylineThickness;

                if (lineLayer == null) {
                    lineLayer = new LineLayer(layerId, sourceId);
                    lineLayer.setProperties(
                            PropertyFactory.lineColor(journeyColor),
                            PropertyFactory.lineWidth(journeyWidth),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    );
                    // Add layer inside lambda (ideally below labels)
                    String firstLabelLayer = findFirstLabelLayerId(style); // Find label layer inside lambda
                    if (firstLabelLayer != null) {
                        style.addLayerBelow(lineLayer, firstLabelLayer);
                    } else {
                        style.addLayer(lineLayer);
                    }
                    Log.d(TAG_LOAD, "Added new layer: " + layerId + " with color " + String.format("#%06X", (0xFFFFFF & journeyColor)) + " width " + journeyWidth);
                } else {
                    lineLayer.setProperties(
                            PropertyFactory.lineColor(journeyColor),
                            PropertyFactory.lineWidth(journeyWidth)
                    );
                    Log.d(TAG_LOAD, "Updated existing layer: " + layerId + " with color " + String.format("#%06X", (0xFFFFFF & journeyColor)) + " width " + journeyWidth);
                }

            } catch (Exception e) {
                Log.e(TAG_LOAD, "Error adding/updating source/layer for journey index " + journeyIndex + " inside getStyle callback", e);
            }
        }); // <<< END getStyle lambda
    }

    /**
     * Calculates summary details for a given journey (list of points).
     * Calculates duration per mode and stores it in JourneyDetails.
     * Also prepares source filenames list (currently empty, needs update in processAndDisplayJourneys).
     */
    private JourneyDetails calculateJourneyDetails(List<PolylinePoint> journeyPoints, List<String> sourceFilenames) {
        final String TAG_LOAD = "JourneyDisplay";
        Log.d(TAG_LOAD, "calculateJourneyDetails: Entered for journey with " + (journeyPoints != null ? journeyPoints.size() : "null") + " points.");

        if (journeyPoints == null || journeyPoints.isEmpty()) {
            Log.w(TAG_LOAD, "calculateJourneyDetails: Returning empty details (null/empty points).");
            // ---> Update constructor call for empty case (6 args) <---
            return new JourneyDetails(0, 0, 0f,
                    new HashMap<String, Long>(),
                    new ArrayList<String>(),
                    new ArrayList<PolylinePoint>(),
                    null, -1.0f, false, null); // Pass null for name
        }

        // --- Basic Details ---
        long startTime = journeyPoints.get(0).timestamp;
        Log.d(TAG_LOAD, "calculateJourneyDetails: Calculating done. Attempting to load name for start time: " + startTime);
        long endTime = (journeyPoints.size() > 1) ? journeyPoints.get(journeyPoints.size() - 1).timestamp : startTime;

        float totalDistance = 0f;
        Location lastLoc = null; // Initialize lastLoc here
        for (PolylinePoint p : journeyPoints) {
            Location currentLoc = new Location(""); // Create Location object for current point
            currentLoc.setLatitude(p.latitude);
            currentLoc.setLongitude(p.longitude);

            if (lastLoc != null) {
                // Add distance between the previous point (lastLoc) and the current point
                totalDistance += currentLoc.distanceTo(lastLoc);
            }
            lastLoc = currentLoc; // Update lastLoc for the next iteration
        }

        // --- Calculate Duration per Mode Map ---
        Map<String, Long> durationPerMode = new HashMap<>();
        if (journeyPoints.size() > 1) {
            for (int i = 0; i < journeyPoints.size() - 1; i++) {
                PolylinePoint p1 = journeyPoints.get(i);
                PolylinePoint p2 = journeyPoints.get(i + 1);
                long segmentDuration = p2.timestamp - p1.timestamp;
                String segmentMode = (p1.transportMode != null && !p1.transportMode.isEmpty()) ? p1.transportMode : "Unknown";
                if (segmentDuration > 0) {
                    long currentTotalDuration = durationPerMode.getOrDefault(segmentMode, 0L);
                    currentTotalDuration += segmentDuration;
                    durationPerMode.put(segmentMode, currentTotalDuration);
                }
            }
        } else { // Handle single point case - add its mode with zero duration? Or leave map empty?
            String singleMode = (journeyPoints.get(0).transportMode != null && !journeyPoints.get(0).transportMode.isEmpty()) ? journeyPoints.get(0).transportMode : "Unknown";
            durationPerMode.put(singleMode, 0L); // Add mode with 0 duration
        }
        Log.d(TAG_LOAD, "Calculated durations per mode: " + durationPerMode.toString());


        // <<< --- START NEW CODE: Load Journey Name from Metadata --- >>>
        String loadedJourneyName = null;
        boolean loadedMapMatched = false;
        String loadedMatchedShape = null;

        Log.d(TAG_LOAD, "calculateJourneyDetails: Attempting to load metadata for start time: " + startTime);
        if (startTime > 0) { // Only attempt load if start time is valid
            String metaFilename = "journey_meta_" + startTime + ".json";
            File directory = getExternalFilesDir(null);
            if (directory != null) {
                File metaFile = new File(directory, metaFilename);
                if (metaFile.exists()) {
                    try (FileReader reader = new FileReader(metaFile)) {
                        // Ensure JourneyMetadata class exists and has a 'name' field/getter
                        JourneyMetadata metadata = gson.fromJson(reader, JourneyMetadata.class);
                        if (metadata != null && metadata.getName() != null) {
                            loadedJourneyName = metadata.getName();
                            loadedMapMatched = metadata.isMapMatched();
                            loadedMatchedShape = metadata.getMatchedShape(); // <<< LOAD SHAPE
                            Log.d(TAG, "Loaded journey name '" + loadedJourneyName + "' from " + metaFilename);
                        } else {
                            Log.w(TAG, "Metadata file " + metaFilename + " loaded but name was null.");
                        }
                        if (loadedJourneyName != null) {
                            Log.d(TAG_LOAD, "calculateJourneyDetails: Loaded name: '" + loadedJourneyName + "'");
                        } else {
                            Log.d(TAG_LOAD, "calculateJourneyDetails: No name loaded, will use default.");
                        }
                    } catch (FileNotFoundException e) {
                        // This shouldn't happen due to metaFile.exists() check, but good practice
                        Log.w(TAG_LOAD, "Metadata file not found during read (unexpected): " + metaFilename);
                    } catch (JsonSyntaxException e) {
                        Log.e(TAG_LOAD, "Error parsing JSON in metadata file: " + metaFilename, e);
                    } catch (Exception e) {
                        Log.e(TAG_LOAD, "Error reading metadata file: " + metaFilename, e);
                    }
                } else {
                    Log.d(TAG_LOAD, "Metadata file not found, using default name: " + metaFilename);
                }
            } else {
                Log.e(TAG_LOAD, "Could not get external files directory to load metadata.");
            }
        }


        // --- Calculate Total Distance ---

        // *** ADD Average Accuracy Calculation variables ***
        float accuracySum = 0f;
        int validAccuracyCount = 0;
        // **********************************************
        for (PolylinePoint p : journeyPoints) {
            Location currentLoc = new Location("");
            currentLoc.setLatitude(p.latitude);
            currentLoc.setLongitude(p.longitude);

            if (lastLoc != null) {
                totalDistance += currentLoc.distanceTo(lastLoc);
            }
            lastLoc = currentLoc;

            // *** Accumulate valid accuracies ***
            if (p.accuracy > 0) {
                accuracySum += p.accuracy;
                validAccuracyCount++;
            }
        }

        // *** Calculate Average Accuracy ***
        float averageAccuracy = (validAccuracyCount > 0) ? (accuracySum / validAccuracyCount) : -1.0f; // Use -1 if no valid points
        // ******************************



        // ... (just before the return statement)
        Log.d(TAG_LOAD, "calculateJourneyDetails: Successfully finished calculations. Returning details.");
// Ensure this is the return line in BOTH calculateJourneyDetails methods:
        return new JourneyDetails(startTime, endTime, totalDistance, durationPerMode,
                sourceFilenames, journeyPoints, loadedJourneyName, averageAccuracy,loadedMapMatched, loadedMatchedShape);
}



    /**
     * Populates the bottom details panel with journey info and makes it visible.
     *
     * @param details    The JourneyDetails object for the selected journey.
     * @param index      The index of the selected journey.
     * @param totalCount The total number of journeys available.
     */
    private void showJourneyDetailsPanel(JourneyDetails details, int index, int totalCount) {
        if (details == null || journeyDetailsPanel == null || tvBottomJourneyModeBreakdown == null || tvBottomJourneyAccuracy == null) { // Add check for new TextView
            Log.e(TAG, "Cannot show details panel - Details, Panel view, or Breakdown TextView is null");
            return;
        }
        Log.d(TAG, "Showing details panel for index: " + index + " / " + totalCount);

        // --- Populate Basic TextViews (as before) ---
        tvBottomJourneyStartTime.setText("Start: " + details.getFormattedStartTime());
        tvBottomJourneyEndTime.setText("End: " + details.getFormattedEndTime());
        tvBottomJourneyDuration.setText("Duration: " + details.getFormattedDuration());
        tvBottomJourneyDistance.setText("Distance: " + details.getFormattedDistance());
        tvBottomJourneyAccuracy.setText(details.getFormattedAverageAccuracy());

        // --- Calculate and Format Mode Percentages ---
        Map<String, Long> durations = details.durationPerModeMs; // Get the map
        if (durations != null && !durations.isEmpty()) {
            long totalDurationMs = 0;
            for (long duration : durations.values()) {
                totalDurationMs += duration;
            }

            if (totalDurationMs > 0) {
                StringBuilder breakdownText = new StringBuilder("Modes: ");
                int modesAdded = 0;
                // Sort modes for consistent display? Optional.
                // List<Map.Entry<String, Long>> sortedEntries = new ArrayList<>(durations.entrySet());
                // sortedEntries.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue())); // Sort descending by duration

                for (Map.Entry<String, Long> entry : durations.entrySet()) { // Or iterate sortedEntries
                    long durationMs = entry.getValue();
                    if (durationMs <= 0) continue; // Skip modes with no duration

                    double percentage = (double) durationMs * 100.0 / totalDurationMs;
                    // Only show modes with >= 1% contribution?
                    if (percentage >= 1.0) {
                        if (modesAdded > 0) {
                            breakdownText.append(", "); // Add separator
                        }
                        breakdownText.append(String.format(Locale.getDefault(), "%s (%.0f%%)", entry.getKey(), percentage));
                        modesAdded++;
                    }
                }
                tvBottomJourneyModeBreakdown.setText(breakdownText.toString());
                tvBottomJourneyModeBreakdown.setVisibility(View.VISIBLE); // Ensure visible
            } else { // Handle case where total duration is zero (e.g., single point)
                String singleMode = details.getDominantMode(); // Use helper if you added it
                tvBottomJourneyModeBreakdown.setText("Mode: " + singleMode);
                tvBottomJourneyModeBreakdown.setVisibility(View.VISIBLE);
            }
        } else {
            // Handle case where duration map is empty or null
            tvBottomJourneyModeBreakdown.setText("Mode: Unknown");
            tvBottomJourneyModeBreakdown.setVisibility(View.VISIBLE);
        }
        // --- End Mode Percentage Logic ---


        // --- Update button states (as before) ---
        btnPrevJourney.setEnabled(index > 0);
        btnNextJourney.setEnabled(index < totalCount - 1);

        tvBottomJourneyName.setText(details.journeyName != null ? details.journeyName : "Journey"); // Use loaded/default name

        currentlyDisplayedDetailIndex = index;
        Log.d(TAG, "showJourneyDetailsPanel: Setting journeyDetailsPanel visibility to VISIBLE"); // <<< ADD THIS LINE
        journeyDetailsPanel.setVisibility(View.VISIBLE);
    }

    /**
     * Hides the bottom details panel and resets any highlight.
     */
    private void hideJourneyDetailsPanel() {
        Log.d(TAG, "Hiding journey details panel..."); // Existing log or add one

        // --- Stop the polyline path animation ---
        if (polylinePathAnimator != null) {
            Log.d(TAG, "Stopping polyline path animation because panel is hiding."); // Add log
            polylinePathAnimator.stopAnimation(); // <<< ADD THIS CALL
        } else {
            Log.w(TAG, "polylinePathAnimator is null in hideJourneyDetailsPanel, cannot stop animation.");
        }
        // -----------------------------------------

        resetHighlight(); // Remove highlight from the polyline (Existing)
        if (journeyDetailsPanel != null) {
            journeyDetailsPanel.setVisibility(View.GONE); // Hide the panel (Existing)
        }
        currentlyDisplayedDetailIndex = -1; // Reset index (Existing)
        Log.d(TAG, "Hid journey details panel."); // Existing log
    }

    private void setupBottomPanelListeners() {
        if (btnCloseDetailsPanel != null) {
            btnCloseDetailsPanel.setOnClickListener(v -> hideJourneyDetailsPanel());
        }

        if (btnPrevJourney != null) {
            btnPrevJourney.setOnClickListener(v -> navigateJourney(-1)); // Pass -1 for previous
        }

        if (btnNextJourney != null) {
            btnNextJourney.setOnClickListener(v -> navigateJourney(1)); // Pass 1 for next
        }

        if (btnDeleteJourney != null) {
            Log.d(TAG, "setupBottomPanelListeners: btnDeleteJourney found, setting listener."); // Existing log is good

            btnDeleteJourney.setOnClickListener(v -> {
                Log.d(TAG, ">>> btnDeleteJourney CLICKED! Current Index: " + currentlyDisplayedDetailIndex); // Log click and index

                if (currentlyDisplayedDetailIndex != -1) {
                    Log.d(TAG, "   Index is valid."); // Log valid index
                    JourneyDetails detailsToDelete = displayedJourneyDetailsList.get(currentlyDisplayedDetailIndex);
                    if (detailsToDelete != null) {
                        Log.d(TAG, "   Got details for StartTime: " + detailsToDelete.startTimeMs); // Log details retrieved
                        showDeleteConfirmationDialog(currentlyDisplayedDetailIndex, detailsToDelete); // Call confirmation
                    } else {
                        Log.e(TAG, "   Error: detailsToDelete object is null for index: " + currentlyDisplayedDetailIndex); // Log null details
                    }
                } else {
                    Log.w(TAG, "   Index is invalid or list empty. Index: " + currentlyDisplayedDetailIndex + ", List size: " + displayedJourneyDetailsList.size()); // Log invalid index
                }
            });
        } else {
            Log.e(TAG, "onCreate: btnDeleteJourney is NULL after findViewById!"); // Keep existing error log
        }

        if (btnEditJourneyName != null) {
            btnEditJourneyName.setOnClickListener(v -> {
                if (currentlyDisplayedDetailIndex != -1 && currentlyDisplayedDetailIndex < displayedJourneyDetailsList.size()) {
                    JourneyDetails detailsToEdit = displayedJourneyDetailsList.get(currentlyDisplayedDetailIndex);
                    showRenameDialog(detailsToEdit); // Call rename dialog method
                } else {
                    Log.w(TAG, "Edit name button clicked but no valid journey index selected.");
                }
            });
        }
    }


    private void showRenameDialog(JourneyDetails journeyToRename) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Journey");

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Enter new journey name");
        input.setText(journeyToRename.journeyName); // Pre-fill with current name
        input.selectAll();
        // Add padding
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        // Set up the buttons
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
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void updateJourneyName(JourneyDetails journeyToUpdate, String newName) {
        int index = -1;
        for (int i = 0; i < displayedJourneyDetailsList.size(); i++) {
            if (displayedJourneyDetailsList.get(i).startTimeMs == journeyToUpdate.startTimeMs) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            // Update name in the main list data object
            displayedJourneyDetailsList.get(index).journeyName = newName;
            // Update the TextView in the currently displayed panel
            if (tvBottomJourneyName != null && currentlyDisplayedDetailIndex == index) {
                tvBottomJourneyName.setText(newName);
            }
            Log.d(TAG, "Updated journey name in list for index: " + index);
            // Trigger background save
            saveJourneyMetadataInBackground(displayedJourneyDetailsList.get(index));
        }
    }

    private void saveJourneyMetadataInBackground(JourneyDetails details) {

        if (details == null) {
            Log.e(TAG, "saveJourneyMetadataInBackground: Cannot save, details object is null.");
            return;
        }

        // Ensure sourceFilenames is populated correctly during loading
        if (details.sourceFilenames == null || details.sourceFilenames.isEmpty()) {
            Log.e(TAG, "Cannot save metadata for journey " + details.startTimeMs + ", sourceFilenames list is missing or empty.");
            // Maybe show a toast error?
            // Toast.makeText(this, "Error saving: Missing file info.", Toast.LENGTH_SHORT).show();
            return;
        }

        final JourneyMetadata metadataToSave = new JourneyMetadata(
                details.journeyName,
                details.sourceFilenames,
                details.startTimeMs,
                details.mapMatched,      // <-- Get mapMatched flag from details
                details.matchedShape     // <-- Get matchedShape from details
        );

        final String metaFilename = "journey_meta_" + details.startTimeMs + ".json";
        Log.d(TAG, "saveJourneyMetadataInBackground: Queuing save for " + metaFilename + " with Matched=" + metadataToSave.isMapMatched() + ", Shape=" + (metadataToSave.getMatchedShape() != null ? "Present" : "Null"));

        if (metadataToSave.getSegmentFilenames() == null || metadataToSave.getSegmentFilenames().isEmpty()) {
            Log.e(TAG, "Cannot save metadata for journey " + details.startTimeMs + ", sourceFilenames list is missing or empty.");
            return;
        }

        backgroundExecutor.execute(() -> {
            File directory = getExternalFilesDir(null);
            if (directory == null) {
                Log.e(TAG, "Cannot save metadata: External directory is null.");
                mainThreadHandler.post(()-> Toast.makeText(MainActivity.this, "Error accessing storage", Toast.LENGTH_SHORT).show());
                return;
            }
            File metaFile = new File(directory, metaFilename);

            try (FileWriter writer = new FileWriter(metaFile)) {
                gson.toJson(metadataToSave, writer); // Serialize the JourneyMetadata object
                Log.i(TAG, "Successfully updated journey metadata in " + metaFilename);
            } catch (Exception e) {
                Log.e(TAG, "Error saving updated journey metadata to " + metaFilename, e);
                mainThreadHandler.post(() -> Toast.makeText(MainActivity.this, "Error saving journey details", Toast.LENGTH_SHORT).show());
            }
        });
    }



    private void showDeleteConfirmationDialog(int indexToDelete, JourneyDetails details) {
        Log.d(TAG, "showDeleteConfirmationDialog called for index: " + indexToDelete);
        String journeyIdentifier = details.getDominantMode() + " journey starting " + details.getFormattedStartTime();

        new AlertDialog.Builder(this)
                .setTitle("Delete Journey?")
                .setMessage("Are you sure you want to permanently delete this " + journeyIdentifier + "?\nThis action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Log.d(TAG, "   Dialog confirmed! Calling deleteJourney for index: " + indexToDelete);
                    deleteJourney(indexToDelete); // Call the actual delete method
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }



    private void deleteJourney(int indexToDelete) {
        Log.d(TAG, ">>> deleteJourney entered for index: " + indexToDelete);

        if (indexToDelete < 0 || indexToDelete >= displayedJourneyDetailsList.size()) {
            Log.e(TAG, "Invalid index provided for deletion: " + indexToDelete);
            return;
        }
        Log.d(TAG, "Attempting to delete journey at index: " + indexToDelete);

        // Make the details object final so it can be accessed inside the lambda
        final JourneyDetails detailsToDelete = displayedJourneyDetailsList.get(indexToDelete);

        // Check if sourceFilenames is valid before proceeding
        if (detailsToDelete.sourceFilenames == null || detailsToDelete.sourceFilenames.isEmpty()) {
            Log.e(TAG, "Cannot delete journey at index " + indexToDelete + ": Source filenames list missing or empty in JourneyDetails.");
            Toast.makeText(this, "Error: Could not find source file(s).", Toast.LENGTH_LONG).show();
            return;
        }

        // Make copies of filenames *final* so they are accessible inside the lambda
        final List<String> filenamesToDelete = new ArrayList<>(detailsToDelete.sourceFilenames);
        final String metaFilenameToDelete = "journey_meta_" + detailsToDelete.startTimeMs + ".json";

        // --- Hide Panel and Reset Highlight ---
        hideJourneyDetailsPanel(); // Call this *before* modifying the list or map

        // --- Delete Files in Background ---
        backgroundExecutor.execute(() -> { // Start of lambda
            Log.d(TAG, "   deleteJourney background task started for index: " + indexToDelete);
            int deleteCount = 0;
            File directory = getExternalFilesDir(null);
            if (directory == null) {
                Log.d(TAG, "   Finished background deletion task. Deleted " + deleteCount + " data file(s). Posting UI update..."); // Modify existing log slightly
                mainThreadHandler.post(() -> Toast.makeText(MainActivity.this, "Error accessing storage", Toast.LENGTH_SHORT).show());
                return; // Exit background task
            }

            // --- Delete Metadata File ---
            File metaFile = new File(directory, metaFilenameToDelete);
            if (metaFile.exists()) {
                if (metaFile.delete()) {
                    Log.d(TAG, "Successfully deleted metadata file: " + metaFilenameToDelete);
                } else {
                    Log.w(TAG, "Failed to delete metadata file: " + metaFilenameToDelete);
                }
            } else {
                Log.w(TAG, "Metadata file not found for deletion: " + metaFilenameToDelete);
            }

            // --- Loop through ALL filenames to delete ---
            // The final variable filenamesToDelete declared outside lambda is now accessible
            Log.d(TAG, "Deleting " + filenamesToDelete.size() + " data file(s) (BG)...");
            for (String filename : filenamesToDelete) { // Accessing final variable
                if (filename == null || filename.isEmpty()) {
                    Log.w(TAG, "Skipping null or empty filename during deletion.");
                    continue;
                }
                File file = new File(directory, filename);
                Log.d(TAG, "Deleting file (BG): " + file.getAbsolutePath());
                if (file.exists()) {
                    if (file.delete()) {
                        Log.d(TAG, "Successfully deleted: " + filename);
                        deleteCount++;
                    } else {
                        Log.w(TAG, "Failed to delete: " + filename);
                    }
                } else {
                    Log.w(TAG, "File not found for deletion: " + filename);
                }
            } // End of for loop

            Log.d(TAG, "Finished background deletion task. Deleted " + deleteCount + " data file(s).");

            // --- Update UI and Data Structures on Main Thread ---
            final int finalDeletedCount = deleteCount; // For toast message if needed
            mainThreadHandler.post(() -> {
                Log.d(TAG, "Updating UI after deletion (Main Thread) for original index: " + indexToDelete);

                // Remove map overlay(s) for this journey index
                clearJourneySegmentsFromMap(indexToDelete);

                // Remove from details list - Use the original indexToDelete
                // Ensure index is still valid *before* removing
                if (displayedJourneyDetailsList != null && indexToDelete < displayedJourneyDetailsList.size()) {
                    // Double-check we are removing the correct item
                    if (displayedJourneyDetailsList.get(indexToDelete).startTimeMs == detailsToDelete.startTimeMs) {
                        displayedJourneyDetailsList.remove(indexToDelete);
                        Log.d(TAG, "Removed details from list. New list size: " + displayedJourneyDetailsList.size());
                        // NOTE: After removing, indices of subsequent items shift.
                        // If multiple deletions happen without refresh, index becomes unreliable.
                        Toast.makeText(MainActivity.this, "Journey deleted.", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "Index mismatch during UI update after deletion! Expected start time " + detailsToDelete.startTimeMs + " at index " + indexToDelete + ", but found different item.");
                        Toast.makeText(MainActivity.this, "Error updating list after deletion.", Toast.LENGTH_SHORT).show();
                        // Consider reloading all data here
                        // isJourneyDataLoaded = false;
                        // if (journeyManager != null) journeyManager.loadAllPolylineData();
                    }
                } else {
                    Log.e(TAG, "Error removing details for index " + indexToDelete + " - list null or index out of bounds during UI update");
                }

                mapView.invalidate(); // Refresh map display
            }); // End mainThreadHandler.post
        }); // End backgroundExecutor.execute
    }


/*// Inside MainActivity.java

/**
 * Handles navigation between journeys using the Previous/Next buttons,
 * skipping journeys hidden by the active filters.
 *
 * @param direction -1 for previous, 1 for next.
 */
private void navigateJourney(int direction) {
    if (currentlyDisplayedDetailIndex == -1 || displayedJourneyDetailsList.isEmpty()) {
        Log.d(TAG, "Navigation ignored: No details currently shown or list is empty.");
        return;
    }

    int potentialNextIndex = currentlyDisplayedDetailIndex; // Start searching from current
    int finalTargetIndex = -1; // Initialize to invalid index

    // Loop forwards or backwards to find the next *visible* journey
    while (true) {
        potentialNextIndex += direction; // Move to next candidate index

        // Check bounds
        if (potentialNextIndex < 0 || potentialNextIndex >= displayedJourneyDetailsList.size()) {
            Log.d(TAG, "Search reached end of list in direction " + direction + ". No further visible journey found.");
            // Optional: Show a toast message
            String message = (direction > 0) ? "Last journey shown" : "First journey shown";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            break; // Exit loop - reached end without finding match
        }

        // Get details for the candidate index
        JourneyDetails candidateDetails = displayedJourneyDetailsList.get(potentialNextIndex);

        // Check if this candidate is visible according to filters
        if (isJourneyVisibleByFilter(candidateDetails)) {
            finalTargetIndex = potentialNextIndex; // Found a visible one!
            Log.d(TAG, "Found next visible journey at index " + finalTargetIndex + " in direction " + direction);
            break; // Exit loop - found target
        } else {
            Log.v(TAG, "Skipping index " + potentialNextIndex + " (Mode: " + (candidateDetails != null ? candidateDetails.getDominantMode() : "null") + ") due to filters.");
            // Continue loop to check the next one
        }
    } // End while loop

    // --- If a valid target index was found ---
    if (finalTargetIndex != -1) {
        Log.d(TAG, "Navigating from index " + currentlyDisplayedDetailIndex + " to " + finalTargetIndex);

        JourneyDetails newDetails = displayedJourneyDetailsList.get(finalTargetIndex);
        if (newDetails == null) {
            Log.e(TAG,"Cannot navigate, details null for target index " + finalTargetIndex);
            return;
        }

        // --- Update UI (Highlight, Panel, Camera, Animation) ---
        resetHighlight();
        highlightJourney(finalTargetIndex, true);
        showJourneyDetailsPanel(newDetails, finalTargetIndex, displayedJourneyDetailsList.size()); // Update panel

        LatLngBounds journeyBounds = calculateJourneyBounds(finalTargetIndex);
        animateCameraToBoundsWithPanelPadding(journeyBounds); // Animate camera

        // Start path animation for the new journey
        List<LatLng> journeyLatLngs = getGeoPointsFromPolylinePoints(newDetails.points);
        if (journeyLatLngs.size() >= 2) {
            List<Point> maplibrePoints = new ArrayList<>();
            for (LatLng ll : journeyLatLngs) {
                maplibrePoints.add(Point.fromLngLat(ll.getLongitude(), ll.getLatitude()));
            }
            LineString lineToAnimate = LineString.fromLngLats(maplibrePoints);
            Log.d(TAG, "navigateJourney: Starting animation for index: " + finalTargetIndex);
            if (polylinePathAnimator != null) {
                polylinePathAnimator.startAnimation(lineToAnimate);
            }
        } else {
            Log.w(TAG,"navigateJourney: Not enough points in journey " + finalTargetIndex + " to animate");
            if (polylinePathAnimator != null) {
                polylinePathAnimator.stopAnimation();
            }
        }
        // --- End UI Update ---

    } else {
        // No visible journey found in the requested direction (already handled by Toast in loop)
        Log.d(TAG, "Navigation finished: No suitable journey found in direction " + direction);
    }
}

    private void setupRetrofit() {
        // Optional: Add logging interceptor for debugging network requests
        // This shows request/response details in Logcat (useful during development)
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY); // Log request and response lines and their respective headers and bodies (if present).

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                // You can add timeouts here if needed:
                // .connectTimeout(30, TimeUnit.SECONDS)
                // .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // Create the Retrofit instance
        retrofit = new Retrofit.Builder()
                // The base URL is required by Retrofit, but less critical since we use @Url in the interface.
                // Still, set it to the server domain.
                .baseUrl("https://valhalla1.openstreetmap.de/")
                .client(client) // Use the OkHttpClient with the logger
                .addConverterFactory(GsonConverterFactory.create()) // Use Gson for JSON conversion
                .build();

        // Create an instance of our API service interface
        valhallaService = retrofit.create(ValhallaService.class);
        Log.d(TAG, "Retrofit and ValhallaService initialized.");
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Handle intent if MainActivity is brought to front again
        setIntent(intent); // Update the activity's intent reference
        handleIntent(intent);
    }

    // Helper method to process the intent
    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra(EXTRA_SELECTED_JOURNEY_START_TIME)) {
            long selectedStartTime = intent.getLongExtra(EXTRA_SELECTED_JOURNEY_START_TIME, -1);
            Log.d(TAG, "handleIntent: Received request to show journey starting at: " + selectedStartTime);


            // Remove the extra so it's not processed again on configuration changes
            intent.removeExtra(EXTRA_SELECTED_JOURNEY_START_TIME);

            if (selectedStartTime != -1 && !displayedJourneyDetailsList.isEmpty()) {
                int journeyIndex = -1;
                LatLngBounds journeyBounds = null; // osmdroid bounding box

                // Find the journey index and its points
                for (int i = 0; i < displayedJourneyDetailsList.size(); i++) {
                    JourneyDetails details = displayedJourneyDetailsList.get(i);
                    if (details.startTimeMs == selectedStartTime) {
                        journeyIndex = i;
                        if (details.points != null && !details.points.isEmpty()) {
                            // Calculate bounds from points
                            List<LatLng> latLngPoints = getGeoPointsFromPolylinePoints(details.points);
                            if (latLngPoints.size() >= 2) {
                                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                for (LatLng point : latLngPoints) {
                                    builder.include(point);
                                }
                                journeyBounds = builder.build();
                            }
                        }
                        break;
                    }
                }

                if (journeyIndex != -1) {
                    final int finalJourneyIndex = journeyIndex; // For use in lambda

                    // Ensure map is ready before zooming/highlighting
                    // Post to map view's handler or use a slight delay if map might not be laid out yet
                    mapView.post(() -> {
                        Log.d(TAG, "handleIntent Runnable: Found journey index " + finalJourneyIndex);

                        if (finalJourneyIndex >= 0 && finalJourneyIndex < displayedJourneyDetailsList.size()) {
                            final JourneyDetails details = displayedJourneyDetailsList.get(finalJourneyIndex); // Make details final for inner Runnable
                            if (details != null) {
                                // 1. Show panel and highlight FIRST
                                showJourneyDetailsPanel(details, finalJourneyIndex, displayedJourneyDetailsList.size());
                                resetHighlight();
                                highlightJourney(finalJourneyIndex, true);

                                // 2. Post camera zoom and polyline animation to the panel's queue
                                //    This increases the chance it's measured before we get height/animate
                                if (journeyDetailsPanel != null) { // Check panel isn't null
                                    journeyDetailsPanel.post(() -> {
                                        Log.d(TAG, "handleIntent Delayed Runnable: Executing zoom and animation for index " + finalJourneyIndex);
                                        // Re-calculate bounds inside here if needed, or use bounds calculated outside post
                                        LatLngBounds journeyBoundsDelayed = calculateJourneyBounds(finalJourneyIndex);

                                        // --- Camera Animation using Helper ---
                                        animateCameraToBoundsWithPanelPadding(journeyBoundsDelayed); // <<< USE HELPER


                                        // --- Polyline Animation ---
                                        List<LatLng> journeyLatLngs = getGeoPointsFromPolylinePoints(details.points);
                                        if (journeyLatLngs.size() >= 2) {
                                            List<Point> maplibrePoints = new ArrayList<>();
                                            for (LatLng ll : journeyLatLngs) maplibrePoints.add(Point.fromLngLat(ll.getLongitude(), ll.getLatitude()));
                                            LineString lineToAnimate = LineString.fromLngLats(maplibrePoints);

                                            Log.d(TAG, "handleIntent Delayed Runnable: Starting animation.");
                                            if (polylinePathAnimator != null) polylinePathAnimator.startAnimation(lineToAnimate);
                                            else Log.w(TAG, "handleIntent Delayed Runnable: polylinePathAnimator is null!");

                                        } else {
                                            Log.w(TAG, "handleIntent Delayed Runnable: Not enough points to animate.");
                                            if (polylinePathAnimator != null) polylinePathAnimator.stopAnimation();
                                        }
                                    }); // End journeyDetailsPanel.post
                                } else {
                                    Log.e(TAG,"handleIntent Runnable: journeyDetailsPanel is null, cannot post delayed actions.");
                                    // Stop animation if panel is missing?
                                    if (polylinePathAnimator != null) polylinePathAnimator.stopAnimation();
                                }
                            } else {
                                Log.e(TAG, "handleIntent Runnable: JourneyDetails null for index " + finalJourneyIndex);
                                resetHighlight();
                                hideJourneyDetailsPanel();
                                if (polylinePathAnimator != null) polylinePathAnimator.stopAnimation();
                            }
                        } else {
                            Log.e(TAG, "handleIntent Runnable: Invalid finalJourneyIndex " + finalJourneyIndex);
                            resetHighlight();
                            hideJourneyDetailsPanel();
                            if (polylinePathAnimator != null) polylinePathAnimator.stopAnimation();
                        }
                    }); // End mapView.post

                } else {
                    Log.w(TAG, "handleIntent: Could not find journey with start time: " + selectedStartTime);
                    Toast.makeText(this, "Could not find selected journey", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    /** // Optional: Update JavaDoc
     * Helper method to convert your PolylinePoint list to MapLibre LatLng list
     * Needed for LatLngBounds calculation and other MapLibre APIs.
     * @param polylinePoints List of your custom PolylinePoint objects.
     * @return List of MapLibre LatLng objects.
     */
    private List<LatLng> getGeoPointsFromPolylinePoints(List<PolylinePoint> polylinePoints) {
        if (polylinePoints == null) { // Handle null input
            return new ArrayList<>();
        }
        List<LatLng> geoPoints = new ArrayList<>(polylinePoints.size());
        for (PolylinePoint p : polylinePoints) {
            geoPoints.add(new LatLng(p.latitude, p.longitude));
        }
        return geoPoints; // Return List<LatLng>
    }


    /**
     * Checks if a single segment needs map matching based on its average point accuracy.
     * You might want to calculate and store average accuracy within SegmentData itself
     * during loading in JourneyManager for efficiency, but for now, we calculate it here.
     */
    private boolean doesSegmentNeedMatching(SegmentData segment, boolean forceRematchOverride) {
        if (forceRematchOverride) {
            // Log.d(TAG_MATCH_CHECK, "Segment " + segment.getOriginalFileName() + " needs matching due to forceRematchOverride.");
            return true; // Force matching if global flag is set
        }
        if (segment == null || segment.getPoints() == null || segment.getPoints().isEmpty()) {
            return false; // Cannot match empty segment
        }

        // --- Calculate Average Accuracy for the segment ---
        float accuracySum = 0f;
        int validAccuracyCount = 0;
        for (PolylinePoint p : segment.getPoints()) {
            if (p.accuracy > 0) {
                accuracySum += p.accuracy;
                validAccuracyCount++;
            }
        }
        float averageAccuracy = (validAccuracyCount > 0) ? (accuracySum / validAccuracyCount) : -1.0f;
        // --- End Accuracy Calculation ---

        boolean needsMatch = (averageAccuracy <= 0 || averageAccuracy > PolylineManager.SKIP_MATCHING_ACCURACY_THRESHOLD);
        // Log.d(TAG_MATCH_CHECK, "Segment " + segment.getOriginalFileName() + " AvgAcc: " + String.format("%.1f", averageAccuracy) + " NeedsMatch? " + needsMatch);
        return needsMatch;
    }

    @Override
    public void onJourneysLoaded(List<SegmentData> sortedSegments, boolean forceRematch) {
        final String TAG_LOAD = "JourneyDisplay";
        final String TAG_MATCH_CHECK = "JourneyDisplay";
        final String TAG_HEATMAP = "HeatmapData"; // Specific tag for heatmap logic

        Log.i(TAG_LOAD, ">>> MainActivity.onJourneysLoaded: CALLBACK RECEIVED! Segments: " + (sortedSegments != null ? sortedSegments.size() : "null") + ", forceRematch=" + forceRematch); // <-- ADD THIS LINE

        // --- Basic Null/Empty Checks & Map/Style Check ---
        if (mapView == null || mapManager == null || maplibreMap == null) {
            Log.e(TAG_LOAD, "onJourneysLoaded (Variable Weighting): Map components are null, stopping display process.");
            onJourneyLoadError("Map components not ready");
            isJourneyDataLoaded = true;
            return;
        }
        Style style = maplibreMap.getStyle();
        if (style == null || !style.isFullyLoaded()) {
            Log.e(TAG_LOAD, "onJourneysLoaded (Variable Weighting): Style not ready, stopping display process.");
            onJourneyLoadError("Map style not ready");
            isJourneyDataLoaded = true;
            return;
        }

        // --- Clear Previous Data ---
        Log.d(TAG_LOAD, "onJourneysLoaded (Variable Weighting): Clearing previous overlays and data...");
        clearAllHistoricalOverlays();
        displayedJourneyDetailsList.clear();

        // --- Handle Empty Segments ---
        if (sortedSegments == null || sortedSegments.isEmpty()) {
            Log.i(TAG_LOAD, "onJourneysLoaded (Variable Weighting): No segments found to process.");
            mapView.invalidate();
            if (heatmapToggleManager != null) {
                heatmapToggleManager.updateHeatmapData(FeatureCollection.fromFeatures(new ArrayList<>()));
                Log.i(TAG_HEATMAP, "Updated heatmap with empty collection as no segments were loaded.");
            }
            isJourneyDataLoaded = true;
            return;
        }
        Log.i(TAG_LOAD, "onJourneysLoaded (Variable Weighting): Processing " + sortedSegments.size() + " raw segments...");


        // <<< --- START: VARIABLE WEIGHT HEATMAP DATA AGGREGATION (GRID METHOD) --- >>>
        Log.d(TAG_HEATMAP, "Starting variable weight heatmap data aggregation...");

        // 1. Define Grid Precision (Number of decimal places for lat/lon)
        // Higher value = finer grid = less aggregation = closer to original density map
        // Lower value = coarser grid = more aggregation = weights reflect frequency more strongly
        final int GRID_PRECISION = 5; // e.g., ~1.1 meter precision. Adjust as needed (4 = ~11m, 3 = ~111m)
        final double factor = Math.pow(10, GRID_PRECISION);

        // 2. Data Structures for Aggregation
        // Map: Grid Cell Key (String) -> Count (Integer)
        Map<String, Integer> pointCounts = new HashMap<>();
        // Map: Grid Cell Key (String) -> Representative Point (MapLibre Point)
        Map<String, Point> representativePoints = new HashMap<>();

        // 3. Iterate and Aggregate Points
        int totalPointsProcessed = 0;
        for (SegmentData segment : sortedSegments) {
            if (segment != null && segment.getPoints() != null) {
                for (PolylinePoint point : segment.getPoints()) {
                    if (point != null) {
                        totalPointsProcessed++;
                        // Round lat/lon to create grid cell key
                        double roundedLat = Math.round(point.latitude * factor) / factor;
                        double roundedLon = Math.round(point.longitude * factor) / factor;
                        // Create a unique key for the grid cell
                        String formatString = String.format(Locale.US, "%%.%df_%%.%df", GRID_PRECISION, GRID_PRECISION); // Creates "%.5f_%.5f" if GRID_PRECISION is 5
                        String gridKey = String.format(Locale.US, formatString, roundedLat, roundedLon);
                        // Increment count for this cell
                        pointCounts.put(gridKey, pointCounts.getOrDefault(gridKey, 0) + 1);

                        // Store the first point encountered in this cell as its representative geometry
                        // (More advanced: calculate centroid, but this is simpler)
                        if (!representativePoints.containsKey(gridKey)) {
                            try {
                                representativePoints.put(gridKey, Point.fromLngLat(point.longitude, point.latitude));
                            } catch (Exception e) {
                                Log.e(TAG_HEATMAP, "Error creating representative point for key " + gridKey, e);
                            }
                        }
                    }
                }
            }
        }
        Log.d(TAG_HEATMAP, "Aggregated " + totalPointsProcessed + " raw points into " + pointCounts.size() + " grid cells (Precision: " + GRID_PRECISION + ").");

        // 4. Create Weighted Features
        List<Feature> heatmapFeatures = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : pointCounts.entrySet()) {
            String gridKey = entry.getKey();
            int count = entry.getValue(); // This is our weight
            Point representativePoint = representativePoints.get(gridKey);

            if (representativePoint != null && count > 0) {
                try {
                    Feature feature = Feature.fromGeometry(representativePoint);
                    feature.addNumberProperty("weight", count); // Use count as weight
                    heatmapFeatures.add(feature);
                } catch (Exception e) {
                    Log.e(TAG_HEATMAP, "Error creating weighted feature for key " + gridKey, e);
                }
            } else {
                Log.w(TAG_HEATMAP, "Skipping feature creation for key " + gridKey + " (Point: " + (representativePoint != null) + ", Count: " + count + ")");
            }
        }

        // 5. Create FeatureCollection and Update Manager
        FeatureCollection heatmapData = FeatureCollection.fromFeatures(heatmapFeatures);
        Log.i(TAG_HEATMAP, "Finished variable weight aggregation. Final features: " + heatmapFeatures.size());

        if (heatmapToggleManager != null) {
            heatmapToggleManager.updateHeatmapData(heatmapData);
            Log.i(TAG_HEATMAP, "Variable weight heatmap data sent to HeatmapToggleManager.");
        } else {
            Log.e(TAG_HEATMAP, "HeatmapToggleManager is null! Cannot update heatmap data.");
        }
        // <<< --- END: VARIABLE WEIGHT HEATMAP DATA AGGREGATION --- >>>


        // --- Grouping and Processing Logic for Polylines (Existing logic remains unchanged) ---
        // ... (The code block starting with List<List<SegmentData>> segmentGroups = ...) ...
        // ... (should remain exactly as it was in the previous step) ...
        List<List<SegmentData>> segmentGroups = new ArrayList<>();
        List<Boolean> groupNeedsMatching = new ArrayList<>();
        int i = 0;
        while (i < sortedSegments.size()) {
            SegmentData currentSegment = sortedSegments.get(i);
            List<SegmentData> currentGroup = new ArrayList<>();
            currentGroup.add(currentSegment);
            boolean currentGroupRequiresMatch = doesSegmentNeedMatching(currentSegment, forceRematch);
            Log.d(TAG_MATCH_CHECK, "Starting group with segment " + i + " (" + currentSegment.getOriginalFileName() + "). Initial Needs Match? " + currentGroupRequiresMatch);
            int j = i + 1;
            while (j < sortedSegments.size()) {
                SegmentData nextSegment = sortedSegments.get(j);
                SegmentData lastSegmentInGroup = currentGroup.get(currentGroup.size() - 1);
                PolylinePoint endPointPrevious = lastSegmentInGroup.getLastPoint();
                PolylinePoint startPointNext = nextSegment.getFirstPoint();
                boolean connect = false;
                boolean nextSegmentRequiresMatch = doesSegmentNeedMatching(nextSegment, forceRematch);
                Log.d(TAG_MATCH_CHECK, "  Checking connection to segment " + j + " (" + nextSegment.getOriginalFileName() + "). Needs Match? " + nextSegmentRequiresMatch);
                if (endPointPrevious != null && startPointNext != null) {
                    long timeGap = startPointNext.timestamp - endPointPrevious.timestamp;
                    float distanceGap = calculateDistance(endPointPrevious, startPointNext);
                    Log.d(TAG_MATCH_CHECK, "    Gap Time: " + timeGap + "ms, Dist: " + String.format("%.1f", distanceGap) + "m"); // Log gap size
                    boolean gapOk = (timeGap >= 0 && timeGap <= MAX_TIME_GAP_MS && distanceGap <= MAX_DISTANCE_GAP_METERS);
                    Log.d(TAG_MATCH_CHECK, "    Gap OK? " + gapOk);
                    if (gapOk) {
                        String previousMode = lastSegmentInGroup.getRepresentativeMode();
                        String nextMode = nextSegment.getRepresentativeMode();
                        Log.d(TAG_MATCH_CHECK, "    Prev Mode: '" + previousMode + "', Next Mode: '" + nextMode + "'");
                        if (!previousMode.equals("Unknown") && previousMode.equals(nextMode)) {
                            connect = true;
                            Log.d(TAG_MATCH_CHECK, "  -> Connecting segment " + j + " to current group. Reason: Gap OK and Modes Match.");
                        } else if (previousMode.equals("Unknown") || !previousMode.equals(nextMode)){
                            Log.d(TAG_MATCH_CHECK, "  -> NOT Connecting segment " + j + ". Reason: Gap OK but Modes Differ or Previous is Unknown.");
                        }
                    } else {
                        Log.d(TAG_MATCH_CHECK, "  -> NOT Connecting segment " + j + ". Reason: Gap too large.");
                    }
                } else {
                    Log.w(TAG_MATCH_CHECK, "    Cannot check gap, null point.");
                }
                if (connect) {
                    currentGroup.add(nextSegment);
                    if (nextSegmentRequiresMatch) {
                        currentGroupRequiresMatch = true;
                        Log.d(TAG_MATCH_CHECK,"    Segment "+j+" requires match, setting group flag.");
                    }
                    j++;
                } else {
                    Log.d(TAG_MATCH_CHECK, "    Decision: BREAK group extension.");
                    break; // Stop extending this group
                }
            } // End inner loop (j)
            boolean finalizedGroupNeedsMatch = forceRematch || currentGroup.size() > 1 || currentGroupRequiresMatch;
            segmentGroups.add(currentGroup);
            groupNeedsMatching.add(finalizedGroupNeedsMatch);
            Log.i(TAG_LOAD, "Finalized Group: Segments " + i + " to " + (j - 1) + ". Final Needs Matching Status: " + finalizedGroupNeedsMatch + " (Size>1: "+(currentGroup.size() > 1)+", AnySegmentNeeded: "+currentGroupRequiresMatch+", Forced: "+forceRematch+")");
            i = j; // Update outer loop index
        } // End outer loop (i)


        Log.d(TAG_LOAD, "onJourneysLoaded: Preparing to loop through " + segmentGroups.size() + " processed groups...");
        final long DELAY_BETWEEN_REQUESTS_MS = 100; // Stagger API calls if needed
        int displayIndex = 0; // Index for adding to displayedJourneyDetailsList and map layers

        // --- Loop through each group of connected segments ---
        for (int groupIdx = 0; groupIdx < segmentGroups.size(); groupIdx++) {
            Log.d(TAG_LOAD, "onJourneysLoaded: Processing group/journey index: " + groupIdx + " (DisplayIndex: " + displayIndex + ")");

            List<SegmentData> group = segmentGroups.get(groupIdx);
            // Get the pre-calculated flag indicating if this group needs matching based on accuracy/segments/gaps
            boolean needsMatchBasedOnCriteria = groupNeedsMatching.get(groupIdx);

            // Combine points and filenames for the entire group
            List<PolylinePoint> combinedPoints = new ArrayList<>();
            List<String> combinedFilenames = new ArrayList<>();
            for (SegmentData segment : group) {
                if (segment.getPoints() != null) combinedPoints.addAll(segment.getPoints());
                combinedFilenames.add(segment.getOriginalFileName());
            }
            // Skip this group if it somehow ended up with no points
            if (combinedPoints.isEmpty()) {
                Log.w(TAG_LOAD, "Skipping group " + groupIdx + " because combinedPoints is empty.");
                continue;
            }

            // Calculate details for the group (This loads mapMatched and matchedShape from Step 1)
            JourneyDetails journeyDetails = calculateJourneyDetails(combinedPoints, combinedFilenames);
            if (journeyDetails == null) {
                Log.w(TAG_LOAD, "Skipping group " + groupIdx + " because calculateJourneyDetails returned null.");
                continue; // Skip if details couldn't be calculated
            }
            // Add the details for this group to the activity's list
            displayedJourneyDetailsList.add(journeyDetails);

            // Create final variables needed for lambdas (postDelayed calls)
            final int currentDisplayIndex = displayIndex++; // Use the current index and increment for the next one
            final JourneyDetails finalJourneyDetails = journeyDetails;
            final List<PolylinePoint> finalCombinedPoints = combinedPoints; // Use the combined points

            // ****** START STEP 2 LOGIC ******
            // Check if we should USE the stored matched shape FIRST
            if (finalJourneyDetails.mapMatched && !forceRematch) {
                Log.i(TAG_LOAD, "Group " + groupIdx + " (DisplayIndex " + currentDisplayIndex + "): Already matched & not forced. Attempting to use stored shape.");
                String storedShape = finalJourneyDetails.getMatchedShape(); // Use getter or direct access if public

                if (storedShape != null && !storedShape.isEmpty()) {
                    // Attempt to decode the stored shape
                    List<LatLng> decodedLatLngs = decodeValhallaPolyline(storedShape);

                    if (decodedLatLngs != null && !decodedLatLngs.isEmpty()) {
                        // SUCCESS: Decoded shape is valid, display it
                        Log.d(TAG_LOAD, "   Successfully decoded stored shape ("+ decodedLatLngs.size() +" points). Displaying matched polyline.");
                        // Use post to ensure map updates happen on the main thread
                        mainThreadHandler.post(() -> {
                            updatePolylineOnMap(currentDisplayIndex, decodedLatLngs, finalJourneyDetails);
                        });
                        continue; // <<<--- IMPORTANT: Skip the rest of this loop iteration!
                    } else {
                        // DECODE FAILED: Log warning and fall through
                        Log.w(TAG_LOAD, "   Failed to decode stored shape for group " + groupIdx + ". Will fall back to checking 'needsMatch'.");
                        // Set needsMatch to true here to force re-matching if decode failed? Optional.
                        needsMatchBasedOnCriteria = true;
                        Log.w(TAG_LOAD, "   Setting needsMatchBasedOnCriteria to true due to decode failure.");
                    }
                } else {
                    // SHAPE MISSING: Log warning and fall through
                    Log.w(TAG_LOAD, "   Stored shape is missing for group " + groupIdx + ". Will fall back to checking 'needsMatch'.");
                    // Set needsMatch to true here to force re-matching if shape was missing? Optional.
                    needsMatchBasedOnCriteria = true;
                    Log.w(TAG_LOAD, "   Setting needsMatchBasedOnCriteria to true due to missing shape.");
                }
                // If we reach here, stored shape was invalid. Execution continues to the 'needsMatch' check below.
            }


            // --- Decision: Match API Call OR Display Raw ---
            // This block is reached if:
            // - mapMatched was false, OR
            // - forceRematch was true, OR
            // - mapMatched was true BUT storedShape was invalid/missing (and needsMatchBasedOnCriteria was potentially set true above)
            // We still rely on the original needsMatchBasedOnCriteria flag calculated during grouping, unless overridden above.
            if (needsMatchBasedOnCriteria) {
                Log.i(TAG_LOAD, ">>> Group " + groupIdx + " (DisplayIndex " + currentDisplayIndex + "): Needs Match. Triggering API call...");
                // Decide between gap or full matching based on combined points
                List<Integer> gapIndices = findLargeGapIndices(finalCombinedPoints);
                if (!gapIndices.isEmpty()) {
                    Log.d(TAG_LOAD, "   Triggering Gap Matching for group " + groupIdx);
                    mainThreadHandler.postDelayed(() -> {
                        processJourneyWithGapMatching(finalCombinedPoints, gapIndices, currentDisplayIndex, finalJourneyDetails);
                    }, groupIdx * DELAY_BETWEEN_REQUESTS_MS);
                } else {
                    Log.d(TAG_LOAD, "   Triggering Full Matching for group " + groupIdx);
                    mainThreadHandler.postDelayed(() -> {
                        callValhallaApi(finalCombinedPoints, currentDisplayIndex, finalJourneyDetails);
                    }, groupIdx * DELAY_BETWEEN_REQUESTS_MS);
                }
            } else {
                // This 'else' means no matching is needed based on the original criteria,
                // AND it wasn't previously matched (or stored shape failed, but criteria still say no match needed - unlikely but possible)
                Log.i(TAG_LOAD, ">>> Group " + groupIdx + " (DisplayIndex " + currentDisplayIndex + "): No match needed by criteria. Displaying raw.");
                mainThreadHandler.postDelayed(() -> {
                    displayRawJourneyFallback(finalCombinedPoints, currentDisplayIndex, finalJourneyDetails);
                }, groupIdx * DELAY_BETWEEN_REQUESTS_MS);
            }
        } // End processing groups loop


        // Inside onJourneysLoaded, near the end after loops
        isJourneyDataLoaded = true; // Mark loading complete
        // Set initial visibility based on current tracking state

        Log.i(TAG_LOAD, "onJourneysLoaded (Variable Weighting): Finished processing all groups. Final displayed polyline list size: " + displayedJourneyDetailsList.size());
        updateHistoricalJourneyVisibility(null); // Apply initial filter state (not tracking yet)
        mapView.invalidate(); // Final invalidation
        isJourneyDataLoaded = true; // Mark loading complete

        // --- ADD A DELAYED CALL INSTEAD ---
        mainThreadHandler.postDelayed(() -> {
            Log.i(TAG_LOAD, "onJourneysLoaded: Applying initial historical visibility (Delayed)");
            String currentModeNow = isTrackingActive ? (overrideMode != null ? overrideMode : "Unknown") : null;
            updateHistoricalPolylinesVisibility(currentModeNow);
        }, 500); // Delay for 500 milliseconds (adjust if needed)
    }




    private void clearAllHistoricalOverlays() {
        final String TAG_LOAD = "JourneyDisplay";
        if (maplibreMap == null) return;

        maplibreMap.getStyle(style -> { // <<< Use getStyle lambda
            Log.d(TAG_LOAD, "Clearing all historical journey layers and sources (within callback)...");

            List<String> layersToRemove = new ArrayList<>();
            List<String> sourcesToRemove = new ArrayList<>();

            // Collect IDs within the callback to avoid issues modifying while iterating
            for (Layer layer : style.getLayers()) {
                if (layer.getId().startsWith(HISTORICAL_LAYER_PREFIX)) { // Use constant prefix
                    layersToRemove.add(layer.getId());
                }
            }
            for (org.maplibre.android.style.sources.Source source : style.getSources()) {
                if (source.getId().startsWith("historical-journey-source-")) {
                    sourcesToRemove.add(source.getId());
                }
            }

            // Remove layers first
            for (String layerId : layersToRemove) {
                if (style.getLayer(layerId) != null) {
                    if (style.removeLayer(layerId)) {
                        Log.d(TAG_LOAD, "Removed layer: " + layerId);
                    } else { Log.w(TAG_LOAD, "Failed to remove layer: " + layerId); }
                }
            }
            // Then remove sources
            for (String sourceId : sourcesToRemove) {
                if (style.getSource(sourceId) != null) {
                    if (style.removeSource(sourceId)) {
                        Log.d(TAG_LOAD, "Removed source: " + sourceId);
                    } else { Log.w(TAG_LOAD, "Failed to remove source: " + sourceId); }
                }
            }
            Log.d(TAG_LOAD, "Finished clearing historical overlays (within callback).");
        }); // <<< END getStyle lambda

        // Clear lists and panel outside the callback (these are UI/data state, not map style)
        displayedJourneyDetailsList.clear();
        highlightedJourneyIndex = -1;
        currentlyDisplayedDetailIndex = -1;
        hideJourneyDetailsPanel();
    }

    @Override
    public void onJourneyLoadError(String errorMessage) {
        final String TAG_LOAD = "JourneyDisplay";
        Log.e(TAG_LOAD, "MainActivity.onJourneyLoadError: Received error callback: " + errorMessage); // <-- Log error callback
        Toast.makeText(this, "Error loading journey data: " + errorMessage, Toast.LENGTH_LONG).show();
        isJourneyDataLoaded = true;
        // Optionally clear the map or handle the error state further
        // processAndDisplayJourneys(new ArrayList<>()); // Example: Show an empty map
    }




    /**
     * Applies highlight styling to the MapLibre LineLayer representing a specific journey.
     * Assumes the layer ID follows the pattern "historical-journey-layer-{journeyIndex}".
     *
     * @param journeyIndex The index of the journey to highlight.
     * @param highlight    True to apply highlight, false to remove highlight (delegated to resetHighlight).
     */
    private void highlightJourney(int journeyIndex, boolean highlight) {
        // If requesting removal, delegate to resetHighlight
        if (!highlight) {
            // Only reset if the requested index is the currently highlighted one
            if (journeyIndex == highlightedJourneyIndex) {
                resetHighlight();
            }
            return;
        }

        // --- Apply Highlight ---
        // Reset any previously highlighted journey first
        resetHighlight();

        // Check map and style readiness
        if (maplibreMap == null) return;
        Style style = maplibreMap.getStyle();
        if (style == null || !style.isFullyLoaded()) {
            Log.w(TAG, "Cannot apply highlight: Style not ready.");
            return;
        }

        // Get the layer ID for the target journey
        String layerId = "historical-journey-layer-" + journeyIndex;
        Log.d(TAG, "Applying highlight for MapLibre layer: " + layerId);

        LineLayer lineLayer = style.getLayerAs(layerId); // Use getLayerAs for type safety

        if (lineLayer != null) {
            // Store original properties (derive from details/settings for simplicity)
            if (journeyIndex >= 0 && journeyIndex < displayedJourneyDetailsList.size()) {
                JourneyDetails details = displayedJourneyDetailsList.get(journeyIndex);
                if (details != null) {
                    originalHighlightedColor = getColorForTransport(details.getDominantMode());
                } else {
                    originalHighlightedColor = Color.GRAY; // Fallback color
                }
                originalHighlightedWidth = (float) polylineThickness; // Use current setting
                Log.d(TAG, "Storing original style for highlight: Color=" + String.format("#%06X", (0xFFFFFF & originalHighlightedColor)) + " Width=" + originalHighlightedWidth);

                // Apply highlight style using PropertyFactory
                lineLayer.setProperties(
                        PropertyFactory.lineColor(HIGHLIGHT_COLOR), // Your defined HIGHLIGHT_COLOR
                        PropertyFactory.lineWidth(originalHighlightedWidth + HIGHLIGHT_WIDTH_INCREASE), // Make it wider
                        PropertyFactory.lineOpacity(1.0f) // Ensure fully opaque
                        // Consider z-index modification later if needed
                );
                highlightedJourneyIndex = journeyIndex; // Track highlighted index
                Log.d(TAG, "Applied highlight style to layer: " + layerId);

            } else {
                Log.w(TAG, "Cannot apply highlight: Invalid journeyIndex " + journeyIndex + " or details not found.");
                highlightedJourneyIndex = -1; // Ensure no index is tracked if details are missing
            }
        } else {
            Log.w(TAG, "Layer not found for highlight update: " + layerId);
            highlightedJourneyIndex = -1; // Reset if layer not found
        }
    }

    /**
     * Removes highlight styling from the currently highlighted MapLibre LineLayer,
     * restoring its original appearance based on journey details and settings.
     */
    private void resetHighlight() {
        // Check if a journey is actually highlighted
        if (highlightedJourneyIndex == -1) {
            return; // Nothing to reset
        }

        // Check map and style readiness
        if (maplibreMap == null) return;
        Style style = maplibreMap.getStyle();
        if (style == null || !style.isFullyLoaded()) {
            Log.w(TAG, "Cannot reset highlight: Style not ready.");
            return;
        }

        // Get the layer ID of the currently highlighted journey
        String layerId = "historical-journey-layer-" + highlightedJourneyIndex;
        Log.d(TAG, "Resetting highlight for MapLibre layer: " + layerId);

        LineLayer lineLayer = style.getLayerAs(layerId); // Use getLayerAs for type safety

        if (lineLayer != null) {
            // Restore original properties (using the stored/derived original values)
            lineLayer.setProperties(
                    PropertyFactory.lineColor(originalHighlightedColor),
                    PropertyFactory.lineWidth(originalHighlightedWidth)
                    // Reset opacity if you changed it during highlight
                    // PropertyFactory.lineOpacity(DEFAULT_OPACITY)
            );
            Log.d(TAG, "Removed highlight style from layer: " + layerId);
        } else {
            Log.w(TAG, "Layer not found for highlight reset: " + layerId);
        }

        // Clear the highlighted index tracker
        highlightedJourneyIndex = -1;
    }

    /**
     * Calculates the bounding box for the polyline associated with a journey index.
     *
     * @param journeyIndex The index of the journey in displayedJourneyDetailsList.
     * @return The LatLngBounds encompassing the journey's points, or null if points are insufficient.
     */
// Replace the previous placeholder version of this method
    private LatLngBounds calculateJourneyBounds(int journeyIndex) {
        if (journeyIndex < 0 || journeyIndex >= displayedJourneyDetailsList.size()) {
            Log.w(TAG, "calculateJourneyBounds: Invalid journey index: " + journeyIndex);
            return null;
        }

        JourneyDetails details = displayedJourneyDetailsList.get(journeyIndex);
        if (details == null || details.points == null || details.points.isEmpty()) {
            Log.w(TAG, "calculateJourneyBounds: No points found for journey index: " + journeyIndex);
            return null;
        }

        // Convert PolylinePoints to MapLibre LatLngs
        List<LatLng> latLngPoints = getGeoPointsFromPolylinePoints(details.points);

        if (latLngPoints.size() < 1) { // Need at least one point for bounds
            Log.w(TAG, "calculateJourneyBounds: Less than 1 LatLng point after conversion for index: " + journeyIndex);
            return null;
        }

        // Use MapLibre's LatLngBounds.Builder
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : latLngPoints) {
            builder.include(point); // Add each point to the builder
        }

        try {
            LatLngBounds bounds = builder.build();
            Log.d(TAG, "Calculated bounds for index " + journeyIndex + ": " + bounds.toString());
            return bounds;
        } catch (Exception e) {
            // builder.build() can throw if points are invalid or too close, handle gracefully
            Log.e(TAG, "Error building LatLngBounds for index " + journeyIndex, e);
            // Fallback: Return bounds containing just the first point? Or null? Null is safer.
            return null;
        }
    }





    // You also need this helper method if it's not already in MainActivity
    private float calculateDistance(PolylinePoint p1, PolylinePoint p2) {
        if (p1 == null || p2 == null)
            return Float.MAX_VALUE; // Return large value if points are null
        float[] results = new float[1];
        try {
            Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error calculating distance between points", e);
            return Float.MAX_VALUE; // Return large value on error
        }
        return results[0];
    }


    /**
     * Makes the actual API call to Valhalla for map matching.
     * Handles the response and updates the map or falls back to raw data.
     */
    private void callValhallaApi(List<PolylinePoint> rawPoints, final int journeyIndex, final JourneyDetails journeyDetails) {
        if (valhallaService == null) {
            Log.e(TAG, "callValhallaApi: ValhallaService is null. Cannot perform matching.");
            displayRawJourneyFallback(rawPoints, journeyIndex, journeyDetails); // Fallback
            return;
        }
        if (rawPoints == null || rawPoints.isEmpty()) {
            Log.w(TAG, "callValhallaApi: No raw points provided for index " + journeyIndex);
            return; // Don't make call with no points
        }
        if (journeyDetails == null) {
            Log.e(TAG, "callValhallaApi: journeyDetails is null for index " + journeyIndex + ". Cannot determine accuracy/costing. Using defaults.");
            // Proceed with defaults or fallback? Let's proceed with defaults for now.
            // displayRawJourneyFallback(rawPoints, journeyIndex, journeyDetails); // Alternative: Fallback
            // return;
        }

        // --- Start of Cut Code ---
        List<ValhallaRequest.ShapePoint> shape = new ArrayList<>();
        for (PolylinePoint p : rawPoints) {
            shape.add(new ValhallaRequest.ShapePoint(p.latitude, p.longitude));
        }
        ValhallaRequest requestBody = new ValhallaRequest(shape);

        // --- START DYNAMIC RADIUS LOGIC ---

        // 1. Define thresholds and radii (adjust values as needed)
        final float GOOD_ACCURACY_THRESHOLD = 15.0f; // Meters (e.g., accuracy <= 15m is good)
        final float POOR_ACCURACY_THRESHOLD = 30.0f; // Meters (e.g., accuracy > 30m is poor)

        final int SMALL_RADIUS = 25;  // Radius for good accuracy (e.g., 25m)
        final int DEFAULT_RADIUS = 50; // Default radius (used for medium or unknown accuracy)
        final int LARGE_RADIUS = 75;  // Radius for poor accuracy (e.g., 75m)

        int radiusToUse = DEFAULT_RADIUS; // Start with default

        // 2. Get average accuracy from JourneyDetails (handle null details)
        float averageAccuracy = (journeyDetails != null) ? journeyDetails.getAverageAccuracy() : -1.0f;

        // 3. Determine radius based on accuracy
        if (averageAccuracy <= 0) {
            // Accuracy is unknown or invalid, use default
            radiusToUse = DEFAULT_RADIUS;
            Log.d(TAG_MATCH_CHECK, "callValhallaApi (Index " + journeyIndex + "): Using DEFAULT search radius ("+ radiusToUse +"m) due to unknown/invalid accuracy (" + String.format("%.1f", averageAccuracy) + "m)");
        } else if (averageAccuracy <= GOOD_ACCURACY_THRESHOLD) {
            // Good accuracy, use smaller radius
            radiusToUse = SMALL_RADIUS;
            Log.d(TAG_MATCH_CHECK, "callValhallaApi (Index " + journeyIndex + "): Using SMALL search radius ("+ radiusToUse +"m) for good accuracy (" + String.format("%.1f", averageAccuracy) + "m)");
        } else if (averageAccuracy > POOR_ACCURACY_THRESHOLD) {
            // Poor accuracy, use larger radius
            radiusToUse = LARGE_RADIUS;
            Log.d(TAG_MATCH_CHECK, "callValhallaApi (Index " + journeyIndex + "): Using LARGE search radius ("+ radiusToUse +"m) for poor accuracy (" + String.format("%.1f", averageAccuracy) + "m)");
        } else {
            // Medium accuracy, use default
            radiusToUse = DEFAULT_RADIUS;
            Log.d(TAG_MATCH_CHECK, "callValhallaApi (Index " + journeyIndex + "): Using DEFAULT search radius ("+ radiusToUse +"m) for medium accuracy (" + String.format("%.1f", averageAccuracy) + "m)");
        }

        // 4. Set the calculated radius on the request body
        requestBody.setSearchRadius(radiusToUse); // Use the setter method

        // --- END DYNAMIC RADIUS LOGIC ---

        // *** ADD DYNAMIC COSTING (Optional but Recommended) ***
        String costingToSet = "auto"; // Default
        if (journeyDetails != null) {
            String dominantMode = journeyDetails.getDominantMode();
            switch (dominantMode) {
                case "Walking":    costingToSet = "pedestrian"; break;
                case "Bicycling":  costingToSet = "bicycle";    break;
                case "In Vehicle": costingToSet = "auto";       break;
                // Add other cases if needed
            }
            Log.d(TAG_MATCH_CHECK, "callValhallaApi (Index " + journeyIndex + "): Determined costing: '" + costingToSet + "' based on dominant mode '" + dominantMode + "'");
        } else {
            Log.w(TAG_MATCH_CHECK, "callValhallaApi (Index " + journeyIndex + "): journeyDetails null, using default costing 'auto'");
        }
        requestBody.setCosting(costingToSet);
        // *** END DYNAMIC COSTING ***


        // *** SET GPS ACCURACY (Optional but Recommended) ***
        if (averageAccuracy > 0 && requestBody.trace_options != null) {
            requestBody.trace_options.gps_accuracy = averageAccuracy;
            Log.d(TAG_MATCH_CHECK, "callValhallaApi (Index " + journeyIndex + "): Setting trace_options.gps_accuracy to: " + String.format("%.1f", averageAccuracy));
        } else {
            Log.d(TAG_MATCH_CHECK, "callValhallaApi (Index " + journeyIndex + "): Not setting gps_accuracy (Avg: " + String.format("%.1f", averageAccuracy) + ", Options Null: " + (requestBody.trace_options == null) + ")");
        }
        // *** END SET GPS ACCURACY ***


        Log.d(TAG, "Making Valhalla API call for journey index: " + journeyIndex + " with " + shape.size() + " points.");
        Call<ValhallaResponse> call = valhallaService.getTraceAttributes(VALHALLA_API_URL, requestBody);



        call.enqueue(new Callback<ValhallaResponse>() {
            @Override
            public void onResponse(Call<ValhallaResponse> call, Response<ValhallaResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ValhallaResponse matchedData = response.body();
                    String encodedShape = matchedData.getShape();
                    if (encodedShape != null && !encodedShape.isEmpty()) {
                        List<LatLng> matchedLatLngs = decodeValhallaPolyline(encodedShape); // Use LatLng
                        if (matchedLatLngs != null && !matchedLatLngs.isEmpty()) {
                            Log.d(TAG, "Valhalla success for index " + journeyIndex + ". Updating map with MATCHED polyline.");
                                if (journeyIndex >= 0 && journeyIndex < displayedJourneyDetailsList.size()) {
                                    JourneyDetails detailsInList = displayedJourneyDetailsList.get(journeyIndex);
                                    detailsInList.mapMatched = true; // Set the flag
                                    detailsInList.setMatchedShape(encodedShape); // Store the shape string
                                    Log.i(TAG_MATCH_CHECK, "Updated JourneyDetails in memory for index " + journeyIndex + " with matched status and shape.");
                                    saveJourneyMetadataInBackground(detailsInList);
                                } else {
                                    Log.e(TAG, "Invalid journeyIndex (" + journeyIndex + ") after successful Valhalla response. Cannot update/save details.");
                                }
                            // --- Update map on main thread ---
                            mainThreadHandler.post(() -> {
                                updatePolylineOnMap(journeyIndex, matchedLatLngs, journeyDetails);
                            });
                        } else {
                            Log.w(TAG, "Valhalla success but decode failed for index " + journeyIndex + ". Falling back to raw single polyline.");
                            displayRawJourneyFallback(rawPoints, journeyIndex, journeyDetails);
                        }
                    } else {
                        Log.w(TAG, "Valhalla success but shape empty for index " + journeyIndex + ". Falling back to raw single polyline.");
                        displayRawJourneyFallback(rawPoints, journeyIndex, journeyDetails);
                    }
                } else {
                    Log.e(TAG, "Valhalla API call failed for index " + journeyIndex + ": " + response.code() + " - " + response.message());
                    try {
                        if (response.errorBody() != null)
                            Log.e(TAG, "Error body: " + response.errorBody().string());
                    } catch (Exception e) {
                    }
                    Log.w(TAG, "Falling back to raw single polyline for index " + journeyIndex + " due to API error.");
                    displayRawJourneyFallback(rawPoints, journeyIndex, journeyDetails);
                }
            }

            @Override
            public void onFailure(Call<ValhallaResponse> call, Throwable t) {
                Log.e(TAG, "Valhalla network request failed for index " + journeyIndex, t);
                Log.w(TAG, "Falling back to raw single polyline for index " + journeyIndex + " due to network error.");
                displayRawJourneyFallback(rawPoints, journeyIndex, journeyDetails);
            }
        });
        // --- End of Cut Code ---
    }

    /**
     * Finds indices of points *before* large time or distance gaps.
     *
     * @param points The list of raw points for the journey.
     * @return A list of integer indices. Empty if no gaps found.
     */
    private List<Integer> findLargeGapIndices(List<PolylinePoint> points) {
        List<Integer> gapIndices = new ArrayList<>();
        if (points == null || points.size() < 2) {
            return gapIndices; // Need at least two points to have a gap
        }
        // Use the constants defined above or adjust as needed
        final float distanceThreshold = MAX_DISTANCE_GAP_METERS_FOR_FORCED_MATCHING;
        final long timeThreshold = GAP_TIME_THRESHOLD_MS;

        for (int i = 0; i < points.size() - 1; i++) {
            PolylinePoint p1 = points.get(i);
            PolylinePoint p2 = points.get(i + 1);
            if (p1 == null || p2 == null) continue; // Skip if points are somehow null

            float distance = calculateDistance(p1, p2); // Assumes calculateDistance helper exists
            long timeDiff = p2.timestamp - p1.timestamp;

            // Check for large distance OR large time gap (and ensure time diff isn't negative)
            if (timeDiff >= 0 && (distance > distanceThreshold || timeDiff > timeThreshold)) {
                Log.i(TAG_MATCH_CHECK, "Gap detected between index " + i + " and " + (i + 1) + ". Distance: " + String.format("%.1f", distance) + "m, Time: " + timeDiff + "ms");
                gapIndices.add(i); // Add index of point *before* the gap
            }
        }
        return gapIndices;
    }

    /**
     * Processes a journey by matching sections around identified gaps and
     * stitching the results with raw segments. Runs network calls sequentially
     * on a background thread. Updates map on main thread.
     */
    private void processJourneyWithGapMatching(final List<PolylinePoint> rawPoints, final List<Integer> gapIndices, final int journeyIndex, final JourneyDetails journeyDetails) {
        final String TAG_MATCH_CHECK = "JourneyDisplay"; // Or use your specific tag
        Log.i(TAG_MATCH_CHECK, ">>> processJourneyWithGapMatching: Entered for index " + journeyIndex + " with " + gapIndices.size() + " gaps.");

        // Run the entire reconstruction process in the background
        backgroundExecutor.execute(() -> {
            List<LatLng> reconstructedPath = new ArrayList<>(); // Use LatLng
            int lastProcessedRawIndex = -1; // Track the end index included from rawPoints

            try {
                Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG Task Start]: Index " + journeyIndex);
                // Iterate through each identified gap index
                for (int gapIndex : gapIndices) {
                    Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG Task Loop]: Processing gap after raw index " + gapIndex);

                    // --- 1. Add Raw Points Before Gap ---
                    int rawSegmentEndIndex = gapIndex;
                    if (rawSegmentEndIndex > lastProcessedRawIndex) {
                        Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Stitching raw points " + (lastProcessedRawIndex + 1) + " to " + rawSegmentEndIndex);
                        for (int i = lastProcessedRawIndex + 1; i <= rawSegmentEndIndex; i++) {
                            if (i < rawPoints.size() && rawPoints.get(i) != null) {
                                reconstructedPath.add(new LatLng(rawPoints.get(i).latitude, rawPoints.get(i).longitude));
                            }
                        }
                    } else {
                        Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: No raw points to add before gap at index " + gapIndex + " (lastProcessedRawIndex=" + lastProcessedRawIndex + ")");
                    }

                    // --- 2. Prepare points FOR the gap matching call ---
                    int gapMatchStartIndex = Math.max(0, gapIndex - GAP_MATCH_CONTEXT_POINTS + 1);
                    int gapMatchEndIndex = Math.min(rawPoints.size() - 1, gapIndex + 1 + GAP_MATCH_CONTEXT_POINTS);
                    List<LatLng> matchedGapPoints = null;

                    if (gapMatchEndIndex > gapMatchStartIndex) {
                        List<PolylinePoint> gapSubList = new ArrayList<>();
                        for (int i = gapMatchStartIndex; i <= gapMatchEndIndex; i++) {
                            if (i < rawPoints.size() && rawPoints.get(i) != null) {
                                gapSubList.add(rawPoints.get(i));
                            }
                        }

                        // Log the points being sent
                        Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Points being sent for gap match (Indices " + gapMatchStartIndex + "-" + gapMatchEndIndex + "):");
                        if (gapSubList != null) {
                            for (int k = 0; k < gapSubList.size(); k++) {
                                PolylinePoint p = gapSubList.get(k);
                                if (p != null) {
                                    Log.d(TAG_MATCH_CHECK, "  -> Point " + k + ": Lat=" + p.latitude + ", Lon=" + p.longitude + ", Acc=" + p.accuracy);
                                } else {
                                    Log.w(TAG_MATCH_CHECK, "  -> Point " + k + " in gapSubList is null!");
                                }
                            }
                        } else {
                            Log.w(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: gapSubList is null before sending!");
                        }
                        // End logging input points

                        if (gapSubList.size() >= 2) {
                            Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Calling sync API for gap after index " + gapIndex + " (raw indices " + gapMatchStartIndex + "-" + gapMatchEndIndex + ")");
                            try {
                                Thread.sleep(300);
                                // Log.d(TAG, "processJourneyWithGapMatching: [Index " + journeyIndex + "] Paused before API call."); // Keep if useful
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt(); /* ... */
                            }
                            matchedGapPoints = callValhallaApiSynchronouslyForGap(gapSubList);
                            Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Sync API result for gap after index " + gapIndex + ": " + (matchedGapPoints == null ? "NULL" : matchedGapPoints.size() + " points"));
                        } else { /* ... log not enough valid points ... */ }
                    } else { /* ... log invalid range ... */ }

                    // --- 4. Add Matched Points (or Raw Fallback) ---
                    if (matchedGapPoints != null && !matchedGapPoints.isEmpty()) {
                        Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Adding " + matchedGapPoints.size() + " matched points for gap after index " + gapIndex);
                        boolean firstMatchPointIsDuplicate = !reconstructedPath.isEmpty() &&
                                reconstructedPath.get(reconstructedPath.size() - 1).equals(matchedGapPoints.get(0));

                        // *** MODIFICATION START: Stop loop one point earlier ***
                        int loopEnd = matchedGapPoints.size() - 1; // Calculate the index *before* the last point

                        // Add points from the matched segment, potentially skipping the first if duplicate, up to loopEnd
                        for (int i = (firstMatchPointIsDuplicate ? 1 : 0); i < loopEnd; i++) { // Use '< loopEnd'
                            // Check if the point is valid before adding (optional but safe)
                            LatLng pointToAdd = matchedGapPoints.get(i);
                            if (pointToAdd != null) { // Basic null check
                                reconstructedPath.add(pointToAdd);
                            } else {
                                Log.w(TAG_MATCH_CHECK, "Skipping null point during matched segment addition at index " + i);
                            }
                        }

                        // Log that the last matched point was intentionally skipped
                        // Check if there was actually a point to skip (i.e., loopEnd was >= the starting index)
                        if (loopEnd > (firstMatchPointIsDuplicate ? 1 : 0)) {
                            Log.w(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Intentionally SKIPPED last matched point (index " + loopEnd + ") to potentially smooth connection.");
                        } else if (matchedGapPoints.size() > 0) {
                            // This case means only 1 point was matched (or 2, and the first was duplicate)
                            // and the loop didn't run. Log this.
                            Log.w(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Not enough matched points added to skip the last one. Matched size: " + matchedGapPoints.size() + ", Skipped first: " + firstMatchPointIsDuplicate);
                            // Consider if you should add the single matched point in this edge case?
                            // If loopEnd is 0 and firstMatchPointIsDuplicate is false, the loop condition (i < 0) is false.
                            // Let's add the first point if the loop didn't run and it wasn't a duplicate.
                            if (!firstMatchPointIsDuplicate && matchedGapPoints.size() > 0 && reconstructedPath.isEmpty()) { // Check if path is still empty
                                reconstructedPath.add(matchedGapPoints.get(0));
                                Log.d(TAG_MATCH_CHECK, "Added the single non-duplicate matched point.");
                            }
                        }

                        lastProcessedRawIndex = gapIndex + 1; // Mark raw point AFTER gap as processed (this remains the same)

                    } else {
                        // Fallback logic if matching failed or returned empty
                        Log.w(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Using raw point fallback for gap after index " + gapIndex);
                        int rawPointAfterGapIndex = gapIndex + 1;
                        // Ensure lastProcessedRawIndex is updated here too if fallback adds a point
                        if (rawPointAfterGapIndex > lastProcessedRawIndex && rawPointAfterGapIndex < rawPoints.size() && rawPoints.get(rawPointAfterGapIndex) != null) {
                            Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Adding raw point at index " + rawPointAfterGapIndex + " as fallback.");
                            reconstructedPath.add(new LatLng(rawPoints.get(rawPointAfterGapIndex).latitude, rawPoints.get(rawPointAfterGapIndex).longitude));
                            lastProcessedRawIndex = rawPointAfterGapIndex; // Mark this raw point as processed
                        } else {
                            Log.w(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Could not add fallback raw point at index " + rawPointAfterGapIndex);
                        }
                    }
                }

                Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [INTERP CHECK]: Checking connection for smoothing...");
                boolean addedInterpolation = false; // Flag to track if we added a point
                if (!reconstructedPath.isEmpty() && (lastProcessedRawIndex + 1) < rawPoints.size()) {
                    LatLng P_match_end = reconstructedPath.get(reconstructedPath.size() - 1);
                    PolylinePoint P_raw_start_poly = rawPoints.get(lastProcessedRawIndex + 1);

                    if (P_raw_start_poly != null) {
                        LatLng P_raw_start_latlng = new LatLng(P_raw_start_poly.latitude, P_raw_start_poly.longitude);
                        float connectionDistance = calculateDistance( // Use your calculateDistance helper
                                new PolylinePoint(P_match_end.getLatitude(), P_match_end.getLongitude(), 0, "", 0f),
                                P_raw_start_poly
                        );

                        // Define thresholds for interpolation (e.g., don't interpolate tiny gaps or huge jumps)
                        float MIN_INTERP_DIST = 1.0f; // meters
                        float MAX_INTERP_DIST = 75.0f; // meters (adjust as needed)

                        if (connectionDistance > MIN_INTERP_DIST && connectionDistance < MAX_INTERP_DIST) {
                            // Calculate midpoint for simple interpolation
                            double interpLat = (P_match_end.getLatitude() + P_raw_start_latlng.getLatitude()) / 2.0;
                            double interpLon = (P_match_end.getLongitude() + P_raw_start_latlng.getLongitude()) / 2.0;
                            LatLng interpolatedPoint = new LatLng(interpLat, interpLon);

                            reconstructedPath.add(interpolatedPoint);
                            addedInterpolation = true; // Mark that we added a point
                            Log.i(TAG_MATCH_CHECK, "processJourneyWithGapMatching [INTERP ADDED]: Added midpoint between matched end and raw start. Dist: " + String.format("%.1f", connectionDistance) + "m");

                        } else {
                            Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [INTERP SKIP]: Distance ("+ String.format("%.1f", connectionDistance) +"m) outside interpolation thresholds ["+MIN_INTERP_DIST+"-"+MAX_INTERP_DIST+"].");
                        }
                    }
                }
                // --- 5. Add Remaining Raw Points ---
                int firstRemainingRawIndex = lastProcessedRawIndex + 1;

                boolean skipFirstRawDueToDuplicate = false; // Initialize to false

// Determine if we should skip the first *raw* point based on exact duplicate check
                if (!reconstructedPath.isEmpty() && firstRemainingRawIndex < rawPoints.size() && rawPoints.get(firstRemainingRawIndex) != null) {
                    LatLng lastAddedPoint = reconstructedPath.get(reconstructedPath.size() - 1);
                    PolylinePoint firstRawPointToAdd = rawPoints.get(firstRemainingRawIndex);
                    double latDiff = Math.abs(lastAddedPoint.getLatitude() - firstRawPointToAdd.latitude);
                    double lonDiff = Math.abs(lastAddedPoint.getLongitude() - firstRawPointToAdd.longitude);
                    // *** Set the flag INSIDE the if block if condition met ***
                    if (latDiff < 0.000001 && lonDiff < 0.000001) {
                        skipFirstRawDueToDuplicate = true;
                        Log.w(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Detected duplicate point at stitch boundary (Index " + firstRemainingRawIndex + "). Skipping first raw point.");
                    }
                }

// Start the loop at firstRemainingRawIndex (which is lastProcessedRawIndex + 1).
// Only advance further (to +2) if the duplicate check specifically flagged it.
// *** Now uses the correctly scoped variable ***
                int loopStartIndex = (addedInterpolation || skipFirstRawDueToDuplicate) ? firstRemainingRawIndex + 1 : firstRemainingRawIndex;

                if (loopStartIndex < rawPoints.size()) {
                    Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: Stitching final raw points starting from index " + loopStartIndex + " (Interpolated=" + addedInterpolation + ", DuplicateSkip=" + skipFirstRawDueToDuplicate + ")");
                    for (int i = loopStartIndex; i < rawPoints.size(); i++) {
                        if (rawPoints.get(i) != null) {
                            reconstructedPath.add(new LatLng(rawPoints.get(i).latitude, rawPoints.get(i).longitude));
                        }
                    }
                } else {
                    Log.d(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG]: No final raw points to add after index adjustment.");
                }

                // --- Determine Success & Encode ---
                boolean successfullyReconstructed = !reconstructedPath.isEmpty();
                String encodedReconstructedShape = null;
                if (successfullyReconstructed) {
                    encodedReconstructedShape = encodeGeoPointsToPolyline6(reconstructedPath);
                    if (encodedReconstructedShape == null) { /* ... log encoding failure ... */ }
                    else { /* ... log encoding success ... */ }
                }

                final List<LatLng> finalReconstructedPath = new ArrayList<>(reconstructedPath);
                final boolean finalSuccessStatus = successfullyReconstructed;
                final String finalEncodedShape = encodedReconstructedShape;
                Log.i(TAG_MATCH_CHECK, "processJourneyWithGapMatching [BG Task END]: Finished for index " + journeyIndex + ". Posting update. Reconstructed=" + finalSuccessStatus);

                // --- 6. Update Map on Main Thread ---
                mainThreadHandler.post(() -> {
                    // ... (Rest of the main thread update logic remains the same) ...
                    Log.i(TAG_MATCH_CHECK, ">>> MainThread Update from GapMatching: Index " + journeyIndex);
                    if (finalReconstructedPath.isEmpty()) { /* ... handle empty path ... */ }
                    else {
                        Log.i(TAG_MATCH_CHECK, "Updating map for journey " + journeyIndex + " with RECONSTRUCTED path (" + finalReconstructedPath.size() + " points).");
                        updatePolylineOnMap(journeyIndex, finalReconstructedPath, journeyDetails);
                        if (finalSuccessStatus && journeyIndex >= 0 && journeyIndex < displayedJourneyDetailsList.size()) {
                            JourneyDetails detailsInList = displayedJourneyDetailsList.get(journeyIndex);
                            detailsInList.mapMatched = true;
                            detailsInList.setMatchedShape(finalEncodedShape);
                            Log.i(TAG_MATCH_CHECK, "Updated JourneyDetails in memory for index " + journeyIndex + " after gap matching. Shape " + (finalEncodedShape != null ? "present." : "missing/encode failed."));
                            saveJourneyMetadataInBackground(detailsInList);
                        } else if (!finalSuccessStatus) { /* ... log skip metadata ... */ }
                        else { /* ... log invalid index error ... */ }
                    }
                }); // End mainThreadHandler.post

            } catch (Exception e) {
                // ... (Existing catch block remains the same) ...
            }
        }); // End backgroundExecutor task
    } // End processJourneyWithGapMatching


    /**
     * Encodes a list of LatLng points into a polyline6 string for Valhalla.
     *
     * @param path The list of LatLng objects to encode.
     * @return The encoded polyline6 string, or null if the input path is null or empty.
     */
    private String encodeGeoPointsToPolyline6(List<LatLng> path) {
        if (path == null || path.isEmpty()) {
            Log.w(TAG, "encodeGeoPointsToPolyline6: Input path is null or empty.");
            return null;
        }

        long lastLat = 0;
        long lastLng = 0;
        double factor = 1E6; // Precision factor for polyline6
        StringBuilder result = new StringBuilder();

        try {
            for (LatLng point : path) {
                long lat = Math.round(point.getLatitude() * factor);
                long lng = Math.round(point.getLongitude() * factor);

                long dLat = lat - lastLat;
                long dLng = lng - lastLng;

                encodeValue(dLat, result);
                encodeValue(dLng, result);

                lastLat = lat;
                lastLng = lng;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error encoding polyline6", e);
            return null; // Return null on error
        }

        Log.d(TAG, "Successfully encoded " + path.size() + " points into polyline6 string.");
        return result.toString();
    }

    /**
     * Helper method to encode a single latitude or longitude value difference.
     * @param value The value (difference * 1E6).
     * @param result The StringBuilder to append the encoded characters to.
     */
    private void encodeValue(long value, StringBuilder result) {
        // Step 2 & 4 from Google's documentation: Calculate signed value
        value = (value < 0) ? ~(value << 1) : (value << 1);
        // Step 5: Chunk into 5-bit segments
        while (value >= 0x20) {
            // Step 6: OR with 0x20 if another chunk follows
            result.append((char) ((0x20 | (value & 0x1f)) + 63));
            // Step 7: Right-shift by 5
            value >>= 5;
        }
        // Step 8: Add final chunk + 63
        result.append((char) (value + 63));
    }


    /**
     * Makes a synchronous Valhalla API call for map matching a sub-list of points.
     * Intended to be called from a background thread. Handles response and decoding.
     * @param pointsToMatch The sub-list of PolylinePoints to match (needs >= 2 points).
     * @return List<GeoPoint> of matched points, or null if matching failed,
     * API returned error, or decoding failed.
     */
    private List<LatLng> callValhallaApiSynchronouslyForGap(List<PolylinePoint> pointsToMatch) {
        final String TAG_MATCH_CHECK = "JourneyDisplay"; // Or use your specific tag
        int pointCount = (pointsToMatch == null) ? 0 : pointsToMatch.size();
        Log.d(TAG_MATCH_CHECK, ">>> callValhallaApiSynchronouslyForGap: Entered with " + pointCount + " points.");
        if (pointCount > 0) {
            Log.d(TAG_MATCH_CHECK, "  First point: " + pointsToMatch.get(0).latitude + "," + pointsToMatch.get(0).longitude + " (Acc: " + pointsToMatch.get(0).accuracy + ")");
            if (pointCount > 1) {
                Log.d(TAG_MATCH_CHECK, "  Last point: " + pointsToMatch.get(pointCount-1).latitude + "," + pointsToMatch.get(pointCount-1).longitude + " (Acc: " + pointsToMatch.get(pointCount-1).accuracy + ")");
            }
        }
        // Basic validation
        if (valhallaService == null) {
            Log.e(TAG, "ValhallaService is null in sync call for gap.");
            return null; // Cannot proceed
        }
        // Valhalla typically needs at least 2 points to match a trace
        if (pointsToMatch == null || pointsToMatch.size() < 2) {
            Log.w(TAG_MATCH_CHECK, "callValhallaApiSynchronouslyForGap: Returning null (Not enough points: " + pointCount + ")"); // Log reason
            return null;
        }

        // Prepare request body
        List<ValhallaRequest.ShapePoint> shape = new ArrayList<>();
        for (PolylinePoint p : pointsToMatch) {
            shape.add(new ValhallaRequest.ShapePoint(p.latitude, p.longitude));
        }
        ValhallaRequest requestBody = new ValhallaRequest(shape);
        // *** START NEW CODE: Customize parameters for GAP matching ***
        // Use a smaller search radius for gaps
        requestBody.setSearchRadius(35); // Let's try 35m for diagnostics
        // Optionally experiment with other parameters if needed:
        // requestBody.costing = "auto_shorter"; // Could try different costing
        // requestBody.shape_match = "route_snap"; // Alternative snapping if data around gap is clean
        // *** END NEW CODE ***

        // Create the Retrofit call object
        Call<ValhallaResponse> call = valhallaService.getTraceAttributes(VALHALLA_API_URL, requestBody);
        Log.d(TAG_MATCH_CHECK, "callValhallaApiSynchronouslyForGap: Executing sync Valhalla call...");

        // Calculate average accuracy for the points being sent for gap match
        double sumAccuracy = 0;
        int validAccuracyCount = 0;
        for (PolylinePoint p : pointsToMatch) {
            if (p.accuracy > 0) {
                sumAccuracy += p.accuracy;
                validAccuracyCount++;
            }
        }
        double averageGapAccuracy = (validAccuracyCount > 0) ? (sumAccuracy / validAccuracyCount) : -1.0; // Use -1 or skip setting if no valid accuracy

// Set gps_accuracy if calculated and trace_options exists
        if (averageGapAccuracy > 0 && requestBody.trace_options != null) {
            requestBody.trace_options.gps_accuracy = averageGapAccuracy;
            Log.d(TAG_MATCH_CHECK, "callValhallaApiSynchronouslyForGap: Setting trace_options.gps_accuracy to: " + String.format("%.1f", averageGapAccuracy));
        } else {
            Log.d(TAG_MATCH_CHECK, "callValhallaApiSynchronouslyForGap: Not setting gps_accuracy (Avg: " + String.format("%.1f", averageGapAccuracy) + ", Options Null: " + (requestBody.trace_options == null) + ")");
            // Ensure gps_accuracy is NOT set if invalid, maybe explicitly set to null if the field allows?
            // Or rely on Valhalla's default if the field isn't added to the request JSON when null/0.
            // If the field MUST exist, you might need to decide on a default value if accuracy is unknown.
        }

        try {
            // *** Execute the call SYNCHRONOUSLY ***
            Response<ValhallaResponse> response = call.execute();
            int responseCode = response.code();
            Log.d(TAG_MATCH_CHECK, "callValhallaApiSynchronouslyForGap: API call executed. Response code: " + responseCode);

            // Process the response
            if (response.isSuccessful() && response.body() != null) {
                ValhallaResponse matchedData = response.body();
                String encodedShape = matchedData.getShape();

                if (encodedShape != null && !encodedShape.isEmpty()) {
                    // Decode the polyline
                    List<LatLng> matchedGeoPoints = decodeValhallaPolyline(encodedShape);

                    // Check if decode was successful and returned points
                    if (matchedGeoPoints != null && !matchedGeoPoints.isEmpty()) {
                        Log.i(TAG_MATCH_CHECK, "callValhallaApiSynchronouslyForGap: SUCCESS - Decoded " + matchedGeoPoints.size() + " points.");
                        return matchedGeoPoints; // Success! Return matched points
                    } else {
                        Log.w(TAG_MATCH_CHECK, "callValhallaApiSynchronouslyForGap: Decode FAILED or returned empty list from shape: " + encodedShape.substring(0, Math.min(encodedShape.length(), 50)) + "...");
                        return null; // Indicate failure: Decode failed
                    }
                } else {
                    Log.w(TAG_MATCH_CHECK, "callValhallaApiSynchronouslyForGap: API Call SUCCESSFUL (Code " + responseCode + ") but NO matched shape returned by Valhalla.");
                    // This might happen if Valhalla couldn't match any part of the input trace
                    return null; // Indicate failure: No shape matched
                }
            } else {
                // Log API error (e.g., 4xx, 5xx)
                Log.e(TAG_MATCH_CHECK, "callValhallaApiSynchronouslyForGap: API Call FAILED - Code: " + responseCode + ", Message: " + response.message());
                try { if (response.errorBody() != null) Log.e(TAG, "Error body: " + response.errorBody().string()); } catch (Exception ignored) {}
                return null; // Indicate failure: API error
            }
        } catch (Exception e) {
            // Log Network (IOException) or other runtime exceptions during execute()
            Log.e(TAG_MATCH_CHECK, "!!! Network/Runtime ERROR in callValhallaApiSynchronouslyForGap !!!", e);
            return null; // Indicate failure: Network/runtime error
        }
    }

    private void showActivityConfidencePopup() {
        String message;
        synchronized (lastDetectedActivities) { // Access the list safely
            if (lastDetectedActivities == null || lastDetectedActivities.isEmpty()) {
                message = "No activity confidence data received yet.";
            } else {
                StringBuilder sb = new StringBuilder();
                // Sort activities by confidence (optional, but makes it clearer)
                List<DetectedActivity> sortedActivities = new ArrayList<>(lastDetectedActivities);
                Collections.sort(sortedActivities, (a1, a2) -> Integer.compare(a2.getConfidence(), a1.getConfidence())); // Descending confidence

                for (DetectedActivity activity : sortedActivities) {
                    sb.append(activityTypeToString(activity.getType())) // Use helper method
                            .append(": ")
                            .append(activity.getConfidence())
                            .append("%\n");
                }
                message = sb.toString().trim(); // Remove trailing newline
            }
        }

        // Use the existing showDialog helper, or create a new AlertDialog directly
        // Using showDialog ensures only one GPS/Info dialog is shown at a time
        gpsStatusDialog = showDialog("Detected Activity Confidences", message);

        // Or create a new one if you want them separate:
    /*
    new AlertDialog.Builder(this)
            .setTitle("Detected Activity Confidences")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    */
    }

    // Helper method to convert activity type to string (copy from Receiver or Service)
    private String activityTypeToString(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE: return "IN_VEHICLE";
            case DetectedActivity.ON_BICYCLE: return "ON_BICYCLE";
            case DetectedActivity.ON_FOOT: return "ON_FOOT";
            case DetectedActivity.RUNNING: return "RUNNING";
            case DetectedActivity.STILL: return "STILL";
            case DetectedActivity.TILTING: return "TILTING";
            case DetectedActivity.UNKNOWN: return "UNKNOWN";
            case DetectedActivity.WALKING: return "WALKING";
            default: return "Unrecognized(" + activityType + ")";
        }
    }

    @Override
    public void onOrientationChanged(boolean isRotated) {
        // Use the member variable updated in onResume
        if (isOrientationLocked) {
            runOnUiThread(() -> {
                if (setNorthButton != null && setNorthButton.getVisibility() == View.VISIBLE) {
                    setNorthButton.setVisibility(View.GONE);

                }
            });
            return; // Don't proceed further if locked
        }

        runOnUiThread(() -> {
            if (setNorthButton != null) {
                int newVisibility = isRotated ? View.VISIBLE : View.GONE;


                    setNorthButton.setVisibility(newVisibility);

            } else {
            }
        });
    }

    private void addMapClickListener(@NonNull MapLibreMap map) {
        map.addOnMapClickListener(point -> { // Lambda expression for the listener
            Log.d(TAG, "Map clicked at LatLng: " + point.toString());
            final PointF screenPoint = map.getProjection().toScreenLocation(point);

            // Query the map for rendered features near the click point
            // We query a small box around the click point and check for our layers
            // Alternatively, you could query specific layer IDs if you know them all.
            List<Feature> features = map.queryRenderedFeatures(screenPoint);

            int clickedJourneyIndex = -1; // Default to -1 (no journey clicked)

            if (!features.isEmpty()) {
                Log.d(TAG, "Found " + features.size() + " features at click point.");
                // Iterate through the clicked features
                for (Feature feature : features) {
                    String layerId = feature.getStringProperty("layer_id"); // MapLibre might add layer ID as property? Check documentation or test.
                    // Alternative: Check feature properties if you added some, or rely on layer query below.

                    // If the feature doesn't directly tell us the layer, we need to refine the query
                    // Query again, specifically for our historical layers within a small radius
                    // Define the pixel region around the touch point to query
                    float density = getResources().getDisplayMetrics().density;
                    float touchRadius = 5 * density; // 5dp radius
                    android.graphics.RectF clickRect = new android.graphics.RectF(
                            screenPoint.x - touchRadius,
                            screenPoint.y - touchRadius,
                            screenPoint.x + touchRadius,
                            screenPoint.y + touchRadius
                    );

                    // Get potential layer IDs
                    List<String> layerIdsToCheck = new ArrayList<>();
                    for(int i=0; i < displayedJourneyDetailsList.size(); i++){
                        layerIdsToCheck.add("historical-journey-layer-" + i);
                    }

                    List<Feature> journeyFeatures = map.queryRenderedFeatures(clickRect, null, layerIdsToCheck.toArray(new String[0]));


                    if (!journeyFeatures.isEmpty()) {
                        Log.d(TAG, "Query found " + journeyFeatures.size() + " historical journey features in click radius.");
                        // Get the first one found (usually sufficient unless lines overlap significantly)
                        Feature clickedJourneyFeature = journeyFeatures.get(0);
                        String clickedLayerId = null;

                        // How to get the layer ID reliably?
                        // Sometimes the layer info is not directly in the feature.
                        // We know we queried *only* our layers, so we can infer the ID if needed,
                        // but ideally the queryRenderedFeatures API helps identify the layer.
                        // Let's *assume* we can get the layer ID. If not, we may need another approach.

                        // Workaround: Iterate through known layer IDs and see which one is present in the result
                        for (String potentialLayerId : layerIdsToCheck) {
                            List<Feature> singleLayerFeatures = map.queryRenderedFeatures(clickRect, potentialLayerId);
                            if (!singleLayerFeatures.isEmpty()) {
                                clickedLayerId = potentialLayerId;
                                Log.d(TAG, "Identified clicked layer: " + clickedLayerId);
                                break; // Found the layer
                            }
                        }


                        if (clickedLayerId != null && clickedLayerId.startsWith("historical-journey-layer-")) {
                            try {
                                // Extract the index from the layer ID
                                String indexStr = clickedLayerId.substring("historical-journey-layer-".length());
                                clickedJourneyIndex = Integer.parseInt(indexStr);
                                Log.d(TAG, "Extracted journey index: " + clickedJourneyIndex);
                                break; // Exit loop once a journey layer is identified
                            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                                Log.e(TAG, "Error parsing journey index from layer ID: " + clickedLayerId, e);
                            }
                        }
                    } else {
                        Log.d(TAG, "Query in click radius found no historical journey features.");
                    }


                } // End of feature iteration (initial query)
            } else {
                Log.d(TAG, "No features found at click point.");
            }


            // --- Handle Click Result ---
            if (clickedJourneyIndex != -1) {
                // Valid journey clicked
                if (clickedJourneyIndex >= 0 && clickedJourneyIndex < displayedJourneyDetailsList.size()) {
                    JourneyDetails details = displayedJourneyDetailsList.get(clickedJourneyIndex);
                    resetHighlight(); // Clear previous highlight
                    highlightJourney(clickedJourneyIndex, true); // Highlight clicked one
                    showJourneyDetailsPanel(details, clickedJourneyIndex, displayedJourneyDetailsList.size()); // Show panel

                    // Optional: Zoom to the journey bounds
                    LatLngBounds journeyBounds = calculateJourneyBounds(clickedJourneyIndex);
                    if (journeyBounds != null && maplibreMap != null) {
                        try {
                            // 1. Get the height of the details panel (make sure it's visible first)
                            // Note: getHeight() might return 0 if called before the panel is measured.
                            // If this happens consistently, we might need a ViewTreeObserver.
                            int panelHeight = 0;
                            if (journeyDetailsPanel != null && journeyDetailsPanel.getVisibility() == View.VISIBLE) {
                                panelHeight = journeyDetailsPanel.getHeight();
                                Log.d(TAG, "Details panel height: " + panelHeight);
                                if (panelHeight == 0) {
                                    // Fallback or warning if height isn't ready
                                    Log.w(TAG, "Journey details panel height is 0, using default bottom padding.");
                                    // You could use a fixed estimate, e.g., 200dp converted to pixels
                                    panelHeight = (int) (200 * getResources().getDisplayMetrics().density);
                                }
                            } else {
                                Log.d(TAG, "Details panel is null or not visible, using 0 bottom padding.");
                            }


                            // 2. Define padding values (adjust as needed)
                            int paddingSides = (int) (75 * getResources().getDisplayMetrics().density); // e.g., 75dp padding for sides/top
                            int paddingTop = paddingSides;
                            int paddingLeft = paddingSides;
                            int paddingRight = paddingSides;
                            int paddingBottom = panelHeight + (int) (20 * getResources().getDisplayMetrics().density); // Panel height + extra 20dp margin


                            Log.d(TAG, "Animating camera with padding: L=" + paddingLeft + ", T=" + paddingTop + ", R=" + paddingRight + ", B=" + paddingBottom);

                            // 3. Use the animateCamera overload with padding
                            maplibreMap.animateCamera(
                                    CameraUpdateFactory.newLatLngBounds(
                                            journeyBounds,
                                            paddingLeft,
                                            paddingTop,
                                            paddingRight,
                                            paddingBottom
                                    ),
                                    1000 // Animation duration in ms (optional)
                            );
                        } catch (Exception e) {
                            Log.e(TAG, "Error zooming to journey bounds with padding", e);
                        }
                    }

                    // Get the points for the clicked journey
                    List<LatLng> journeyLatLngs = getGeoPointsFromPolylinePoints(details.points);
                    if (journeyLatLngs.size() >= 2) {
                        // Convert to MapLibre LineString
                        List<Point> maplibrePoints = new ArrayList<>();
                        for (LatLng ll : journeyLatLngs) {
                            maplibrePoints.add(Point.fromLngLat(ll.getLongitude(), ll.getLatitude()));
                        }
                        LineString lineToAnimate = LineString.fromLngLats(maplibrePoints);
                        Log.d(TAG, "Attempting to start animation for index: " + clickedJourneyIndex); // <-- ADD LOG
                        // ---> Use the Animator Class <---
                        if (polylinePathAnimator != null) {
                            polylinePathAnimator.startAnimation(lineToAnimate); // Pass the line
                        }
                    } else {
                        // ---> Use the Animator Class <---
                        if (polylinePathAnimator != null) {
                            polylinePathAnimator.stopAnimation();
                        }
                    }

                } else {
                    Log.e(TAG, "Clicked journey index " + clickedJourneyIndex + " is out of bounds for details list size " + displayedJourneyDetailsList.size());
                    hideJourneyDetailsPanel(); // Hide panel if index is invalid
                    if (polylinePathAnimator != null) {
                        polylinePathAnimator.stopAnimation();
                    }
                }
            } else {
                // Clicked on the map, but not on a historical journey polyline
                resetHighlight(); // Clear any highlight
                hideJourneyDetailsPanel(); // Hide the details panel
                if (polylinePathAnimator != null) {
                    polylinePathAnimator.stopAnimation();
                }
            }

            return true; // Indicate the click was handled
        });
        Log.d(TAG,"Map click listener added.");
    }

    private void setupAnimationLayer(@NonNull Style style) {
        // Use the constants defined as member variables in MainActivity
        final String ARROW_SOURCE_ID = "direction-arrow-source";
        final String ARROW_LAYER_ID = "direction-arrow-layer";
        final String ARROW_ICON_ID = "direction-arrow-icon"; // Ensure this matches MapManager

        GeoJsonSource arrowSource; // Local variable for setup
        SymbolLayer arrowLayer; // Local variable for setup

        Log.d(TAG, "Running setupAnimationLayer..."); // Log entry

        if (style.getSource(ARROW_SOURCE_ID) == null) {
            // Source needs initial valid GeoJSON - create an initial empty point feature
            arrowSource = new GeoJsonSource(ARROW_SOURCE_ID, Feature.fromGeometry(Point.fromLngLat(0, 0)));
            style.addSource(arrowSource);
            Log.i(TAG, "SetupAnimationLayer: ADDED source '" + ARROW_SOURCE_ID + "' successfully.");
        } else {
            // Source exists, just get a reference (cast might be needed depending on how PolylinePathAnimator accesses it)
            arrowSource = style.getSourceAs(ARROW_SOURCE_ID); // Assuming PolylinePathAnimator will also get it this way
            Log.i(TAG, "SetupAnimationLayer: FOUND existing source '" + ARROW_SOURCE_ID + "'.");
        }

        if (style.getLayer(ARROW_LAYER_ID) == null) {
            arrowLayer = new SymbolLayer(ARROW_LAYER_ID, ARROW_SOURCE_ID)
                    .withProperties(
                            PropertyFactory.iconImage(ARROW_ICON_ID), // Use the constant
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconSize(0.8f), // Adjust size
                            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                            PropertyFactory.visibility(Property.NONE) // Initially hidden
                    );
            // Add layer (Use simple addLayer for testing top visibility)
            style.addLayer(arrowLayer);
            Log.i(TAG, "SetupAnimationLayer: ADDED layer '" + ARROW_LAYER_ID + "' with icon '" + ARROW_ICON_ID + "' successfully.");
        } else {
            // Layer exists, just ensure visibility is NONE initially
            arrowLayer = style.getLayerAs(ARROW_LAYER_ID);
            if (arrowLayer != null) {
                arrowLayer.setProperties(PropertyFactory.visibility(Property.NONE));
                Log.i(TAG, "SetupAnimationLayer: FOUND existing layer '" + ARROW_LAYER_ID + "'. Set visibility NONE.");
            } else {
                Log.e(TAG, "SetupAnimationLayer: Layer '" + ARROW_LAYER_ID + "' exists but is not a SymbolLayer!");
            }
        }
        Log.d(TAG, "Direction arrow source/layer setup attempt complete.");
    }

    // Add this new helper method inside MainActivity
    private void animateCameraToBoundsWithPanelPadding(LatLngBounds bounds) {
        if (bounds == null || maplibreMap == null) {
            Log.w(TAG, "Cannot animate camera: Bounds or Map is null.");
            return;
        }

        try {
            // Get panel height (it should be visible at this point)
            int panelHeight = 0;
            int density = (int) getResources().getDisplayMetrics().density;
            if (journeyDetailsPanel != null && journeyDetailsPanel.getVisibility() == View.VISIBLE) {
                panelHeight = journeyDetailsPanel.getHeight();
                if (panelHeight == 0) {
                    panelHeight = (int) (200 * density); // Fallback height if not measured yet
                }
            }

            // Define padding values
            int paddingSides = 75 * density;
            int paddingBottom = panelHeight + (20 * density); // Panel height + margin

            Log.d(TAG, "Animating camera (Helper) with padding: B=" + paddingBottom);
            maplibreMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                            bounds, paddingSides, paddingSides, paddingSides, paddingBottom
                    ), 1000 // Duration
            );
        } catch (Exception e) {
            Log.e(TAG, "Error animating camera with panel padding", e);
        }
    }

    // Inside MainActivity.java

    /**
     * Updates the GeoJsonSource for the current track with the latest points.
     */
    private void updateCurrentPolylineSource() {
        Log.d(TAG, ">>> updateCurrentPolylineSource ENTERED. currentTrackLatLngs size: " + currentTrackLatLngs.size()); // <-- ADD LOG
        if (maplibreMap == null) return;
        Style style = maplibreMap.getStyle();
        if (style == null || !style.isFullyLoaded()) return;

        GeoJsonSource source = style.getSourceAs(CURRENT_TRACK_SOURCE_ID);
        if (source != null) {
            Log.d(TAG, "Found source: " + CURRENT_TRACK_SOURCE_ID); // <-- ADD LOG
            if (currentTrackLatLngs.size() >= 2) {
                // Convert List<LatLng> to List<Point> for LineString
                List<Point> mapboxPoints = new ArrayList<>(currentTrackLatLngs.size());
                for (LatLng latLng : currentTrackLatLngs) {
                    mapboxPoints.add(Point.fromLngLat(latLng.getLongitude(), latLng.getLatitude()));
                }
                LineString lineString = LineString.fromLngLats(mapboxPoints);
                Log.d(TAG, "Updating source with LineString (" + mapboxPoints.size() + " points)"); // <-- ADD LOG
                source.setGeoJson(Feature.fromGeometry(lineString));
            } else if (currentTrackLatLngs.size() == 1) {
                // If only one point, update source with just that point (won't draw line, but source is updated)
                Point singlePoint = Point.fromLngLat(currentTrackLatLngs.get(0).getLongitude(), currentTrackLatLngs.get(0).getLatitude());
                source.setGeoJson(Feature.fromGeometry(singlePoint));
                Log.d(TAG, "Updating source with single Point"); // <-- ADD LOG
            }
            else {
                // If list is empty, clear the source
                source.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(new ArrayList<>())));
                Log.d(TAG, "Clearing source (0 points)"); // <-- ADD LOG
            }
        } else {
            Log.w(TAG, "Cannot update current polyline source: Source '" + CURRENT_TRACK_SOURCE_ID + "' not found."); // Keep existing log
        }
    }

    /**
     * Updates the style (color, width) of the current track layer based on the mode.
     * @param currentMode The effective mode determining the style.
     */
    private void updateCurrentPolylineStyle(String currentMode) {
        if (maplibreMap == null) return;
        Style style = maplibreMap.getStyle();
        if (style == null || !style.isFullyLoaded()) {
            Log.w(TAG, "updateCurrentPolylineStyle: Style not ready.");
            return;
        }

        LineLayer layer = style.getLayerAs(CURRENT_TRACK_LAYER_ID);
        if (layer != null) {
            int color = getColorForTransport(currentMode); // Use your existing helper
            float width = (float) polylineThickness; // Use loaded setting

            // Apply new properties
            layer.setProperties(
                    PropertyFactory.lineColor(color),
                    PropertyFactory.lineWidth(width)
                    // Add other properties like opacity if needed
            );
            Log.v(TAG, "Updated current track layer style. Mode: " + currentMode + ", Color: " + String.format("#%06X", (0xFFFFFF & color)));
        } else {
            Log.w(TAG, "updateCurrentPolylineStyle: Layer '" + CURRENT_TRACK_LAYER_ID + "' not found.");
        }
    }

    /**
     * Updates the color of the current track layer based on the transport mode.
     *
     * @param transportMode The current transport mode string.
     */
    private void updateCurrentPolylineLayerColor(String transportMode) {
        if (maplibreMap == null) return;
        Style style = maplibreMap.getStyle();
        if (style == null || !style.isFullyLoaded()) return;

        LineLayer layer = style.getLayerAs(CURRENT_TRACK_LAYER_ID);
        if (layer != null) {
            int newColor = getColorForTransport(transportMode);
            // Optional: Check if color actually changed before setting
            // Object currentColorObj = layer.getLineColor().getValue(); // Getting color can be complex
            // if (currentColorObj instanceof Integer && ((Integer)currentColorObj) == newColor) return;

            layer.setProperties(PropertyFactory.lineColor(newColor));
            Log.v(TAG, "Updated current track layer color for mode: " + transportMode); // Verbose log
        } else {
            Log.w(TAG, "Cannot update current polyline layer color: Layer '" + CURRENT_TRACK_LAYER_ID + "' not found.");
        }
    }

    // Inside MainActivity.java

    /**
     * Removes the MapLibre source and layer associated with a specific historical journey index.
     * Gets the style asynchronously.
     */
    private void clearJourneySegmentsFromMap(int journeyIndex) {
        final String TAG_LOAD = "JourneyDisplay";
        if (maplibreMap == null) {
            Log.w(TAG_LOAD, "clearJourneySegmentsFromMap: MapLibreMap is null, cannot clear for index " + journeyIndex);
            return;
        }
        // Use getStyle lambda
        maplibreMap.getStyle(style -> {
            clearJourneySegmentsFromMapInternal(style, journeyIndex); // Call internal helper
        });
    }

    /**
     * Internal helper to remove layer and source using a provided Style object.
     */
    private void clearJourneySegmentsFromMapInternal(@NonNull Style style, int journeyIndex) {
        final String TAG_LOAD = "JourneyDisplay";
        // Define the IDs based on the index
        String layerId = "historical-journey-layer-" + journeyIndex;
        String sourceId = "historical-journey-source-" + journeyIndex;

        Log.d(TAG_LOAD, "Attempting to clear layer '" + layerId + "' and source '" + sourceId + "' using provided style.");

        try {
            // Remove layer first (if it exists)
            Layer layer = style.getLayer(layerId); // Use provided style
            if (layer != null) {
                if (style.removeLayer(layer)) {
                    Log.d(TAG_LOAD, "Removed layer: " + layerId);
                } else {
                    Log.w(TAG_LOAD, "Failed to remove layer: " + layerId);
                }
            } else {
                Log.d(TAG_LOAD, "Layer already removed or never existed: " + layerId);
            }

            // Then remove source (if it exists)
            org.maplibre.android.style.sources.Source source = style.getSource(sourceId); // Use provided style
            if (source != null) {
                if (style.removeSource(source)) {
                    Log.d(TAG_LOAD, "Removed source: " + sourceId);
                } else {
                    Log.w(TAG_LOAD, "Failed to remove source: " + sourceId);
                }
            } else {
                Log.d(TAG_LOAD, "Source already removed or never existed: " + sourceId);
            }

        } catch (Exception e) {
            Log.e(TAG_LOAD, "Error removing layer/source for index " + journeyIndex + " inside internal clear", e);
        }
    }

    /**
     * Updates the visibility of historical polyline layers based on the current setting
     * and the active tracking mode (if applicable).
     *
     * @param currentTrackingMode The mode currently being tracked live (e.g., "Walking", "Unknown"),
     * or null if tracking is inactive.
     */
    private void updateHistoricalPolylinesVisibility(@Nullable String currentTrackingMode) {
        if (maplibreMap == null) {
            Log.w(TAG, "updateHistoricalPolylinesVisibility: maplibreMap is null, cannot proceed."); // LOG ADDED
            return;
        }
        final boolean isCurrentlyTracking = isTrackingActive; // Use MainActivity's flag
        final int setting = historicalVisibilitySetting; // Use loaded setting

        Log.i(TAG, ">>> updateHistoricalPolylinesVisibility ENTERED. Tracking=" + isCurrentlyTracking + ", Setting=" + setting + ", CurrentMode=" + currentTrackingMode);

        maplibreMap.getStyle(style -> { // Operate within the style callback
            Log.d(TAG, "updateHistoricalPolylinesVisibility: Inside getStyle callback."); // LOG ADDED
            if (style == null || !style.isFullyLoaded()) {
                Log.w(TAG, "updateHistoricalPolylinesVisibility: Style not ready in callback.");
                return;
            }
            Log.d(TAG, "updateHistoricalPolylinesVisibility: Looping through " + displayedJourneyDetailsList.size() + " displayed journeys."); // LOG ADDED
            for (int i = 0; i < displayedJourneyDetailsList.size(); i++) {
                String layerId = HISTORICAL_LAYER_PREFIX + i;
                Layer layer = style.getLayer(layerId);
                Log.v(TAG, "updateHistoricalPolylinesVisibility: Processing index " + i + ", LayerID=" + layerId + ", Layer found? " + (layer != null)); // LOG ADDED

                if (layer instanceof LineLayer) {
                    String targetVisibility = Property.VISIBLE; // Default to visible

                    if (isCurrentlyTracking) { // Apply rules only if tracking is active
                        Log.v(TAG, "updateHistoricalPolylinesVisibility: Applying rules for Active Tracking. Setting=" + setting);
                        if (setting == SettingsActivity.MODE_HIDE_ALL) {
                            targetVisibility = Property.NONE;
                        } else if (setting == SettingsActivity.MODE_HIDE_UNRELATED) {
                            boolean isCurrentModeActiveMovement = currentTrackingMode != null &&
                                    (currentTrackingMode.equals("Walking") ||
                                            currentTrackingMode.equals("Bicycling") ||
                                            currentTrackingMode.equals("In Vehicle"));

                            if (isCurrentModeActiveMovement) { // Only apply hiding rule if actively moving
                                if (i < displayedJourneyDetailsList.size()) {
                                    JourneyDetails historicalDetails = displayedJourneyDetailsList.get(i);
                                    if (historicalDetails != null) {
                                        String historicalMode = historicalDetails.getDominantMode();
                                        // Hide if historical mode is known and differs from the active current mode
                                        if (!historicalMode.equals("Unknown") &&
                                                !historicalMode.equals(currentTrackingMode)) {
                                            targetVisibility = Property.NONE;
                                        }
                                    }

                                }
                            }
                        }
                        // If setting is MODE_SHOW_ALL, targetVisibility remains VISIBLE
                    }
                    // If not tracking, targetVisibility remains VISIBLE (show all)

                    // Apply the determined visibility
                    layer.setProperties(PropertyFactory.visibility(targetVisibility));
                    Log.v(TAG, "Set layer " + layerId + " visibility to " + targetVisibility);
                } else if (layer != null) {
                    Log.w(TAG, "Layer " + layerId + " found but is not a LineLayer.");
                }
                // No need to log if layer is null, might happen if data reloads
            }
            Log.i(TAG, ">>> updateHistoricalPolylinesVisibility FINISHED processing layers."); // LOG ADDED
        });
    }

    // Inside MainActivity.java

    /**
     * Updates the visibility of historical journey layers based on BOTH
     * the current tracking state/settings AND the active filter buttons.
     *
     * @param currentTrackingMode The mode currently being tracked live ("Walking", etc.),
     * or null if tracking is inactive.
     */
    private void updateHistoricalJourneyVisibility(@Nullable String currentTrackingMode) {
        if (maplibreMap == null) {
            Log.w(TAG, "updateHistoricalJourneyVisibility: maplibreMap is null.");
            return;
        }
        final boolean isCurrentlyTracking = isTrackingActive; // Use MainActivity's tracking flag
        final int setting = historicalVisibilitySetting; // Use loaded setting

        // Use local copies of filter state for thread safety within the lambda
        final boolean showWalk = filterWalkActive;
        final boolean showBike = filterBikeActive;
        final boolean showVehicle = filterVehicleActive;
        // No need for showAll here, logic depends on individual flags

        Log.i(TAG, ">>> updateHistoricalJourneyVisibility ENTERED. Tracking=" + isCurrentlyTracking
                + ", Setting=" + setting + ", CurrentMode=" + currentTrackingMode
                + ", Filters[W:" + showWalk + " B:" + showBike + " V:" + showVehicle + "]");

        maplibreMap.getStyle(style -> { // Operate within the style callback
            Log.d(TAG, "updateHistoricalJourneyVisibility: Inside getStyle callback.");
            if (style == null || !style.isFullyLoaded()) {
                Log.w(TAG, "updateHistoricalJourneyVisibility: Style not ready in callback.");
                return;
            }
            Log.d(TAG, "updateHistoricalJourneyVisibility: Looping through " + displayedJourneyDetailsList.size() + " displayed journeys.");

            for (int i = 0; i < displayedJourneyDetailsList.size(); i++) {
                String layerId = HISTORICAL_LAYER_PREFIX + i;
                Layer layer = style.getLayer(layerId);

                if (layer instanceof LineLayer) {
                    boolean isVisible = true; // Start assuming visible

                    // --- Filter Button Logic ---
                    JourneyDetails historicalDetails = (i < displayedJourneyDetailsList.size()) ? displayedJourneyDetailsList.get(i) : null;
                    if (historicalDetails != null) {
                        String historicalMode = historicalDetails.getDominantMode();
                        boolean filterAllows = false;
                        if (historicalMode.equals("Walking") && showWalk) filterAllows = true;
                        else if (historicalMode.equals("Bicycling") && showBike) filterAllows = true;
                        else if (historicalMode.equals("In Vehicle") && showVehicle) filterAllows = true;
                        else if (historicalMode.equals("Unknown") || historicalMode.equals("Still")) {
                            // Decide how to handle Unknown/Still - show if 'All' is checked? Or always show?
                            // Let's show them if any filter is active, effectively treating them as "Other"
                            // Or maybe only if filterAllActive is true? Let's try showing if any filter is on.
                            filterAllows = showWalk || showBike || showVehicle;
                            // Alternatively: filterAllows = filterAllActive;
                        }

                        if (!filterAllows) {
                            isVisible = false; // Hide if filters don't allow this mode
                            Log.v(TAG, " Hiding layer " + layerId + " due to filter state.");
                        }
                    } else {
                        isVisible = false; // Hide if details are missing
                        Log.w(TAG, " Hiding layer " + layerId + " because JourneyDetails are null.");
                    }

                    // --- Tracking State Logic (Only apply if filter allows visibility) ---
                    if (isVisible && isCurrentlyTracking) {
                        // Apply Hide All / Hide Unrelated rules
                        if (setting == SettingsActivity.MODE_HIDE_ALL) {
                            isVisible = false;
                            Log.v(TAG, " Hiding layer " + layerId + " due to MODE_HIDE_ALL setting.");
                        } else if (setting == SettingsActivity.MODE_HIDE_UNRELATED) {
                            boolean isCurrentModeActiveMovement = currentTrackingMode != null &&
                                    (currentTrackingMode.equals("Walking") ||
                                            currentTrackingMode.equals("Bicycling") ||
                                            currentTrackingMode.equals("In Vehicle"));

                            if (isCurrentModeActiveMovement && historicalDetails != null) {
                                String historicalMode = historicalDetails.getDominantMode();
                                // Hide if historical mode is known and differs from the active current mode
                                if (!historicalMode.equals("Unknown") &&
                                        !historicalMode.equals(currentTrackingMode)) {
                                    isVisible = false;
                                    Log.v(TAG, " Hiding layer " + layerId + " due to MODE_HIDE_UNRELATED setting (Current: " + currentTrackingMode + ", Historical: " + historicalMode + ")");
                                }
                            }
                        }
                    }

                    // --- Apply Final Visibility ---
                    String targetVisibility = isVisible ? Property.VISIBLE : Property.NONE;
                    layer.setProperties(PropertyFactory.visibility(targetVisibility));
                    Log.v(TAG, " Set layer " + layerId + " final visibility to " + targetVisibility);

                } else if (layer != null) {
                    Log.w(TAG, "Layer " + layerId + " found but is not a LineLayer.");
                }
            }
            Log.i(TAG, ">>> updateHistoricalJourneyVisibility FINISHED processing layers.");
        });
    }

    // Helper to get the current mode for passing to the visibility function
    private String getCurrentEffectiveMode() {
        return isTrackingActive ? (overrideMode != null ? overrideMode : "Unknown") : null;
        // Note: You might want a more sophisticated way to get the *actual* last detected
        // mode from the service if override is null and tracking is active, perhaps store it?
        // For now, "Unknown" is a safe default if tracking but no override.
    }

    // Inside MainActivity.java

    /**
     * Checks if a journey should be visible based on the current mode filter settings.
     * @param details The JourneyDetails of the journey to check.
     * @return true if the journey's mode matches an active filter, false otherwise.
     */
    private boolean isJourneyVisibleByFilter(JourneyDetails details) {
        if (details == null) {
            return false; // Cannot filter null details
        }
        String historicalMode = details.getDominantMode();

        // Check against the active filter flags (member variables)
        if (historicalMode.equals("Walking") && filterWalkActive) return true;
        if (historicalMode.equals("Bicycling") && filterBikeActive) return true;
        if (historicalMode.equals("In Vehicle") && filterVehicleActive) return true;

        // Decide how to handle Unknown/Still - show if *any* main filter is active?
        // This makes them appear unless all filters are off. Adjust if needed.
        if ((historicalMode.equals("Unknown") || historicalMode.equals("Still")) &&
                (filterWalkActive || filterBikeActive || filterVehicleActive)) {
            return true;
        }

        // Default: not visible if mode doesn't match any active filter
        return false;
    }

} // --- End of MainActivity ---