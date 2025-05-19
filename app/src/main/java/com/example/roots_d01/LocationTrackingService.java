package com.example.roots_d01;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager; // Use LocalBroadcastManager

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Task;
import com.google.gson.Gson;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Binder;

import org.maplibre.android.geometry.LatLng;

public class LocationTrackingService extends Service {

    private static final String TAG = "LocationTrackingService";
    private static final String TAG_SYNC = "StateSyncDebug";
    private static final String TAG_LATCH = "ActivityLatchDebug"; // New Tag for latching logic

    private static final String CHANNEL_ID = "LocationTrackingServiceChannel";
    private static final int NOTIFICATION_ID = 12345; // Unique ID

    // --- Constants for Broadcasts ---
    // MainActivity will listen for this action
    public static final String ACTION_LOCATION_BROADCAST = "com.example.roots_d01.action.LOCATION_BROADCAST";
    // Key for the Location object in the broadcast Intent's extras
    public static final String EXTRA_LOCATION = "com.example.roots_d01.extra.LOCATION";
    // Optional: Define constants for other status broadcasts if needed
    public static final String ACTION_GPS_DISABLED = "com.example.roots_d01.action.GPS_DISABLED";

    public static final String ACTION_LOCATION_PERMISSION_ERROR = "com.example.roots_d01.action.LOCATION_PERMISSION_ERROR";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    // Location update parameters (tune as needed)
    private static final long LOCATION_UPDATE_INTERVAL = 2000;  // 2 seconds
    private static final float LOCATION_UPDATE_DISTANCE = 2;    // 2 meters


    // --- Background Processing & Saving ---
    private Gson gson;
    private ExecutorService backgroundExecutor;

    // --- Activity Recognition ---
    private ActivityRecognitionClient activityRecognitionClient;
    private static final long ACTIVITY_DETECTION_INTERVAL = 5000; // ms

    // Static variables for the Receiver to update (simple approach, has limitations)
    public static volatile int latestActivityType = DetectedActivity.UNKNOWN;
    public static volatile int latestActivityConfidence = 0;
    // Inside LocationTrackingService.java
    private PolylineManager polylineManager;
    public static final String EXTRA_EFFECTIVE_MODE = "com.example.roots_d01.extra.EFFECTIVE_MODE";
    private Handler mainThreadHandler;

    public static final String EXTRA_NEW_GEOPOINT_LAT = "com.example.roots_d01.extra.NEW_GEOPOINT_LAT";
    public static final String EXTRA_NEW_GEOPOINT_LON = "com.example.roots_d01.extra.NEW_GEOPOINT_LON";
    public static final double INVALID_LAT_LON = -999.0; // A value indicating no new point was added
    private String currentOverrideMode = null; // Stores the override mode sent from MainActivity
    private BroadcastReceiver overrideReceiver; // Receiver for the override broadcast
    public static final String ACTION_ACTIVITY_UPDATE = "com.example.roots_d01.action.ACTIVITY_UPDATE";
    public static final String EXTRA_DETECTED_ACTIVITY_TYPE = "com.example.roots_d01.extra.DETECTED_ACTIVITY_TYPE";
    public static final String EXTRA_DETECTED_ACTIVITY_CONFIDENCE = "com.example.roots_d01.extra.DETECTED_ACTIVITY_CONFIDENCE";

    // Inside LocationTrackingService.java class definition
    private boolean isAutoTrackingCurrentlyActive = false; // Track if service is auto-tracking
    private BroadcastReceiver activityUpdateReceiver; // Receiver for internal activity updates

    // For debouncing stop requests
    private Handler stopDelayHandler = new Handler(Looper.getMainLooper());
    private Runnable stopRunnable = null;
    private static final long AUTO_STOP_DELAY_MS = 30000; // e.g., 30 seconds of STILL before stopping
    public static final String ACTION_TRACKING_STATE_CHANGED = "com.example.roots_d01.action.TRACKING_STATE_CHANGED";
    public static final String EXTRA_IS_TRACKING = "com.example.roots_d01.extra.IS_TRACKING";
    private final IBinder binder = new LocalBinder();
    // Add near other broadcast constants
    public static final String ACTION_ACTIVITY_DETECTED = "com.example.roots_d01.action.ACTIVITY_DETECTED";
    public static final String EXTRA_DETECTED_ACTIVITY_STRING = "com.example.roots_d01.extra.DETECTED_ACTIVITY_STRING";

    public static final String EXTRA_IS_RECORDING_ACTIVE = "com.example.roots_d01.extra.IS_RECORDING_ACTIVE"; // <<< ADD THIS LINE

    final int MOVING_CONFIDENCE_THRESHOLD = 65;
    final int WALKING_ACTIVITY_CONFIDENCE_THRESHOLD = 75; // Used for Running, Walking, Bicycling, In Vehicle
    final int BICYCLING_ACTIVITY_CONFIDENCE_THRESHOLD = 75; // Used for Running, Walking, Bicycling, In Vehicle
    final int IN_VEHICLE_ACTIVITY_CONFIDENCE_THRESHOLD = 75; // Used for Running, Walking, Bicycling, In Vehicle
    private String potentialNextMode = null; // Stores a mode detected but not yet confirmed
    private long potentialModeStartTime = 0; // Timestamp when potentialNextMode was first detected
    private long lastConfirmedModeTime = 0; // Timestamp of the last confirmed mode change
    // --- Constants for Debouncing/Filtering ---
    // How long a new potential mode must be detected before confirming the change for PolylineManager
    private static final long MIN_DURATION_FOR_MODE_CHANGE_MS = 8 * 1000; // 8 seconds
    // How long to hold onto the last confirmed mode during brief UNKNOWN/STILL periods
    private static final long MAX_DURATION_TO_KEEP_LAST_MODE_MS = 15 * 1000; // 15 seconds
    // How long a moving activity needs to be detected before auto-starting
    private static final long MIN_DURATION_FOR_AUTO_START_MS = 6 * 1000; // 6 seconds (adjust as needed)
    private String effectiveModeForPolyline = "Unknown"; // <<< ADD THIS LINE
    private String lastConfirmedMode = "Unknown"; // Correct initialization

    // --- Latching and Timeout State (NEW) ---
    private String latchedMode = null; // Stores "In Vehicle" or "Bicycling" when latched
    private long stillUnknownStartTime = 0L; // Timestamp when Still/Unknown detected after latch
    private Handler stillUnknownTimeoutHandler;
    private Runnable stillUnknownTimeoutRunnable;
    // Timeout duration (e.g., 2 minutes - ADJUST AS NEEDED)
    private static final long STILL_UNKNOWN_TIMEOUT_MS = 2 * 60 * 1000;
    private Location lastLocationForSpeed = null; // Store last location for speed check
    // Speed thresholds in meters/second (ADJUST AS NEEDED)
    private static final float MAX_SPEED_FOR_STILL_RESET_MS = 1.5f; // ~5.4 km/h
    private static final float MIN_SPEED_FOR_VEHICLE_RESET_MS = 2.0f; // ~7.2 km/h
    private static final float MAX_SPEED_FOR_BICYCLE_RESET_MS = 10.0f; // ~36 km/h
    // Define the new broadcast action
    public static final String ACTION_NEW_JOURNEY_SAVED = "com.example.roots_d01.action.NEW_JOURNEY_SAVED";
    // Optional: If you want to pass the start time of the newly saved journey
    public static final String EXTRA_NEW_JOURNEY_START_TIME = "com.example.roots_d01.extra.NEW_JOURNEY_START_TIME";



    final int STILL_CONFIDENCE_THRESHOLD = 85;
    @Override
    public void onCreate() {
        super.onCreate();
        Log.w("AutoTrack_Service", "SERVICE onCreate() CALLED"); // Use WARN
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        Log.d(TAG, "FusedLocationProviderClient initialized."); // Optional log
        createNotificationChannel(); // Create channel once when service is created

        // Initialize dependencies
        gson = new Gson(); // Initialize Gson
        backgroundExecutor = Executors.newSingleThreadExecutor(); // Initialize Executor
        mainThreadHandler = new Handler(Looper.getMainLooper()); // Initialize Handler
        stillUnknownTimeoutHandler = new Handler(Looper.getMainLooper());

        // Initialize Activity Recognition Client here too
        initializeActivityRecognition();

        // Modify the PolylineManager creation to pass the handler
        Log.d(TAG, "Creating PolylineManager instance.");
        polylineManager = new PolylineManager(getApplicationContext(), backgroundExecutor, gson, mainThreadHandler); // Pass the handler
        setupOverrideReceiver();
        setupActivityUpdateReceiver();
        createLocationCallback(); // Ensure callback is created AFTER handlers
    }

    // ... onStartCommand ...
    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand. Flags=" + flags + ", StartId=" + startId);
        Log.w("AutoTrack_Service", "SERVICE onStartCommand() CALLED"); // Use WARN
        Notification notification = createForegroundNotification();
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "Service started in foreground");

        // Request Location Updates
        startTracking();
        // Request Activity Updates (ensure permission is granted first by Activity)
        requestActivityUpdatesInternal();

        return START_STICKY;
    }

    private void startTracking() {
        // Check permissions first
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permissions not granted. Cannot start tracking.");
            Intent permIntent = new Intent(ACTION_LOCATION_PERMISSION_ERROR);
            LocalBroadcastManager.getInstance(this).sendBroadcast(permIntent);
            stopSelf(); // Stop service if permissions are missing
            return;
        }

        // Set the tracking flag to true, even for manual starts via this method
        if (!this.isAutoTrackingCurrentlyActive) { // Only log/notify if changing state
            Log.w(TAG_SYNC, "SERVICE: startTracking() called. Setting isAutoTrackingCurrentlyActive=true"); // Use a distinct log tag if desired
            this.isAutoTrackingCurrentlyActive = true;
            notifyTrackingStateChange(true); // Notify listeners (like MainActivity)
        }

        // Create Location Request (Correct)
        // Note: Builder requires play-services-location v21+
        // Use create() for compatibility if needed: LocationRequest.create()...
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setMinUpdateDistanceMeters(LOCATION_UPDATE_DISTANCE)
                .build();

        // Create Location Callback if needed (Correct)
        if (locationCallback == null) {
            createLocationCallback();
        }

        // Request Updates using FLP (Correct)
        try {
            Log.i(TAG, "startTracking: Requesting location updates via FusedLocationProviderClient...");
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                    .addOnSuccessListener(aVoid -> Log.i(TAG, "startTracking: Fused location updates requested successfully."))
                    .addOnFailureListener(e -> Log.e(TAG, "startTracking: Failed to request fused location updates.", e));
        } catch (SecurityException e) {
            Log.e(TAG, "startTracking: SecurityException requesting fused location updates!", e);
            this.isAutoTrackingCurrentlyActive = false;
            notifyTrackingStateChange(false);
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "startTracking: UNEXPECTED Exception requesting fused location updates!", e);
            this.isAutoTrackingCurrentlyActive = false;
            notifyTrackingStateChange(false);
            stopSelf();
        }
    }

    // Add this helper method inside LocationTrackingService
    private void notifyTrackingStateChange(boolean isNowTracking) {
        // You could optionally add a check here to only broadcast if the state *actually* changed,
        // but sending it anyway is fine as the receiver should handle checking if an update is needed.
        Log.d(TAG, "Notifying tracking state change: isNowTracking = " + isNowTracking);
        Intent intent = new Intent(ACTION_TRACKING_STATE_CHANGED);
        intent.putExtra(EXTRA_IS_TRACKING, isNowTracking);
        Log.w("AutoTrack_Service", "SENDING ACTION_TRACKING_STATE_CHANGED broadcast. EXTRA_IS_TRACKING = " + isNowTracking);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private Notification createForegroundNotification() {
        // Intent to launch MainActivity when notification is clicked
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Roots Tracking Active")
                .setContentText("Tracking your path...")
                .setSmallIcon(R.drawable.ic_stat_notification) // Use the icon you created in Step 1
                .setContentIntent(pendingIntent) // Set the click action
                .setOngoing(true) // Make it non-dismissable
                .setPriority(NotificationCompat.PRIORITY_LOW) // Use low priority
                .build();
    }






    // --- Notification Channel Creation (for Android 8.0+) ---
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking Service Channel", // User-visible name in Settings
                    NotificationManager.IMPORTANCE_LOW // Low importance = less intrusive
            );
            serviceChannel.setDescription("Channel for Roots background location tracking"); // User-visible description

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                Log.d(TAG,"Notification channel created.");
            } else {
                Log.e(TAG,"Failed to get NotificationManager to create channel.");
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service onBind called."); // Add log
        return binder; // <-- Return the binder instance
    }

    /**
     * Called by MainActivity (via Binder) to get the points of the current track segment.
     * @return A list of PolylinePoint objects for the current segment, or an empty list.
     */
    public List<PolylinePoint> getCurrentTrackPoints() {
        if (polylineManager != null) {
            Log.d(TAG, "getCurrentTrackPoints() called by client. Fetching from PolylineManager.");
            return polylineManager.getCurrentSegmentPoints();
        } else {
            Log.e(TAG, "getCurrentTrackPoints() called, but PolylineManager is null!");
            return new ArrayList<>(); // Return empty list if manager is somehow null
        }
    }

    // --- Activity Recognition Methods ---
    private void initializeActivityRecognition() {
        Log.d(TAG, "Initializing Activity Recognition Client");
        activityRecognitionClient = ActivityRecognition.getClient(this);
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    private void requestActivityUpdatesInternal() {
        Log.d("AutoTrack_Service","requestActivityUpdatesInternal: Entered method."); // Changed log slightly for clarity

        if (activityRecognitionClient == null) {
            Log.e("AutoTrack_Service", "AR Client is null, cannot request updates.");
            return;
        }

        // Explicitly log permission check process
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d("AutoTrack_Service", "Checking AR permission (Q+)..."); // Log before check
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                Log.w("AutoTrack_Service","AR permission check RESULT: NOT GRANTED. Returning."); // Explicit log
                return; // Don't request if no permission
            } else {
                Log.d("AutoTrack_Service","AR permission check RESULT: GRANTED."); // Explicit log
            }
        } else {
            Log.d("AutoTrack_Service","Assuming AR permission granted (pre-Q).");
        }


        try {
            PendingIntent pi = getActivityDetectionPendingIntent();
            Log.d("AutoTrack_Service", "About to call requestActivityUpdates..."); // Log before the call
            Task<Void> task = activityRecognitionClient.requestActivityUpdates(
                    ACTIVITY_DETECTION_INTERVAL,
                    pi); // Use service's context

            task.addOnSuccessListener(aVoid -> Log.d("AutoTrack_Service", "requestActivityUpdates: SUCCESS listener triggered.")); // Clarified log message
            task.addOnFailureListener(e -> Log.e("AutoTrack_Service", "requestActivityUpdates: FAILURE listener triggered.", e)); // Clarified log message, includes exception
        } catch (SecurityException e) {
            Log.e("AutoTrack_Service","SecurityException requesting activity updates. Check permission.", e);
        } catch (Exception e) { // Add a general catch block just in case
            Log.e("AutoTrack_Service","Exception during requestActivityUpdates call.", e);
        }
    }

    private void handleActivityChange(int activityType, int confidence) {
        Log.d(TAG_LATCH, "--- handleActivityChange START ---");
        Log.d(TAG_LATCH, "Raw Detection: Type=" + activityTypeToString(activityType) + ", Conf=" + confidence + ", Current Latch: " + latchedMode);
        long currentTime = System.currentTimeMillis();

        // Determine instantaneous mode (same as before)
        String detectedModeNow = "Unknown";
        if (activityType == DetectedActivity.WALKING && confidence >= WALKING_ACTIVITY_CONFIDENCE_THRESHOLD) detectedModeNow = "Walking";
        else if (activityType == DetectedActivity.ON_BICYCLE && confidence >= BICYCLING_ACTIVITY_CONFIDENCE_THRESHOLD) detectedModeNow = "Bicycling";
        else if (activityType == DetectedActivity.IN_VEHICLE && confidence >= IN_VEHICLE_ACTIVITY_CONFIDENCE_THRESHOLD) detectedModeNow = "In Vehicle";
        else if (activityType == DetectedActivity.STILL && confidence >= STILL_CONFIDENCE_THRESHOLD) detectedModeNow = "Still";

        Log.d(TAG_LATCH, "Instantaneous Mode: " + detectedModeNow);
        broadcastInstantaneousActivity(detectedModeNow); // Broadcast for UI

        // --- Latching Logic ---

        // 1. IMMEDIATE WALKING OVERRIDE
        if (detectedModeNow.equals("Walking")) {
            Log.i(TAG_LATCH, "WALKING detected. Overriding latch/timeout.");
            clearLatchAndTimeout(); // Clear latch state and cancel timer
            effectiveModeForPolyline = "Walking";
            lastConfirmedMode = "Walking";
            handleAutoStartStop(detectedModeNow, currentTime); // Update auto-start/stop based on Walking
            Log.d(TAG_LATCH, "--- handleActivityChange END (Walking Override) ---");
            return; // Processed Walking, exit
        }

        // 2. CHECK IF CURRENTLY LATCHED
        if (latchedMode != null) {
            if (detectedModeNow.equals(latchedMode)) {
                // Still detecting the latched mode - confirm and reset timeout
                Log.d(TAG_LATCH, "Confirmed latched mode: " + latchedMode + ". Resetting potential timeout.");
                clearStillUnknownTimeout(); // Cancel timer, reset start time
                effectiveModeForPolyline = latchedMode; // Continue using latched mode
                lastConfirmedMode = latchedMode;
                handleAutoStartStop(detectedModeNow, currentTime); // Cancel any pending stop
            } else if (detectedModeNow.equals("Still") || detectedModeNow.equals("Unknown")) {
                // Detected Still/Unknown while latched
                Log.d(TAG_LATCH, "Detected " + detectedModeNow + " while latched on " + latchedMode);
                effectiveModeForPolyline = latchedMode; // KEEP using latched mode during timeout
                lastConfirmedMode = latchedMode; // Keep confirmed mode as latched for now
                if (stillUnknownStartTime == 0L) {
                    // First time Still/Unknown is detected since latch confirmation
                    Log.i(TAG_LATCH, "Starting Still/Unknown timeout (" + STILL_UNKNOWN_TIMEOUT_MS + "ms)...");
                    stillUnknownStartTime = currentTime;
                    initializeAndStartLatchTimeout(); // Start the timeout runnable
                } else {
                    Log.d(TAG_LATCH, "Still/Unknown timeout already running.");
                }
                // Defer auto-stop decision
            } else {
                // Detected a DIFFERENT *moving* mode (Bicycle <-> Vehicle)
                Log.i(TAG_LATCH, "Different MOVING mode (" + detectedModeNow + ") detected while latched on " + latchedMode + ". Breaking latch.");
                clearLatchAndTimeout(); // Break the latch, cancel timer
                // Fall through to the "Not Latched" logic below to handle the new mode
                handleNotLatched(detectedModeNow, currentTime);
            }
        } else {
            // 3. NOT CURRENTLY LATCHED
            handleNotLatched(detectedModeNow, currentTime);
        }

        Log.d(TAG_LATCH, "Final Effective Mode for Polyline: " + effectiveModeForPolyline);
        Log.d(TAG_LATCH, "--- handleActivityChange END ---");
    }

    /** Handles state updates when not currently latched. */
    private void handleNotLatched(String detectedModeNow, long currentTime) {
        if (detectedModeNow.equals("In Vehicle") || detectedModeNow.equals("Bicycling")) {
            // Start latching
            Log.i(TAG_LATCH, "Initiating LATCH for mode: " + detectedModeNow);
            latchedMode = detectedModeNow;
            effectiveModeForPolyline = latchedMode;
            lastConfirmedMode = latchedMode;
            clearStillUnknownTimeout(); // Ensure no old timeout is running
        } else if (detectedModeNow.equals("Still") || detectedModeNow.equals("Unknown")) {
            // Handle Still/Unknown when not latched
            Log.d(TAG_LATCH, "Detected " + detectedModeNow + " (not latched).");
            effectiveModeForPolyline = detectedModeNow;
            lastConfirmedMode = detectedModeNow;
            clearStillUnknownTimeout();
        } else {
            // Other low confidence or unhandled modes
            Log.d(TAG_LATCH, "Detected other/low confidence mode: " + detectedModeNow + " (not latched). Using Unknown.");
            effectiveModeForPolyline = "Unknown"; // Default for other cases
            lastConfirmedMode = "Unknown";
            clearStillUnknownTimeout();
        }
        // Update auto-start/stop based on the detected mode
        handleAutoStartStop(detectedModeNow, currentTime);
    }

    /** Helper to clear latch state and cancel the timeout timer. */
    private void clearLatchAndTimeout() {
        Log.d(TAG_LATCH, "Clearing latch (was " + latchedMode + ") and cancelling timeout.");
        latchedMode = null;
        if (stillUnknownTimeoutHandler != null && stillUnknownTimeoutRunnable != null) { // Check handler/runnable null
            stillUnknownTimeoutHandler.removeCallbacks(stillUnknownTimeoutRunnable);
        }
        stillUnknownStartTime = 0L;
        lastLocationForSpeed = null; // Also clear location used for speed check
    }

    /** Helper to cancel the timeout timer and reset its start time. */
    private void clearStillUnknownTimeout() {
        if (stillUnknownTimeoutHandler != null && stillUnknownTimeoutRunnable != null) { // Check handler/runnable null
            stillUnknownTimeoutHandler.removeCallbacks(stillUnknownTimeoutRunnable);
        }
        stillUnknownStartTime = 0L;
        // Don't clear latchedMode here
    }

    /** Initializes and starts the runnable for the latch timeout */
    private void initializeAndStartLatchTimeout() {
        // Create the Runnable if it doesn't exist
        if (stillUnknownTimeoutRunnable == null) {
            stillUnknownTimeoutRunnable = () -> {
                Log.i(TAG_LATCH, "Still/Unknown TIMEOUT EXPIRED.");
                // Check if we were still latched when the timer fired
                if (latchedMode != null && stillUnknownStartTime > 0) {
                    Log.i(TAG_LATCH, "Timeout expired while latched on " + latchedMode + ". Switching mode to Still.");
                    String modeBeforeTimeout = latchedMode; // Store for potential auto-stop check
                    clearLatchAndTimeout(); // Clear latch state
                    effectiveModeForPolyline = "Still"; // Set effective mode
                    lastConfirmedMode = "Still";
                    // Now that latch is cleared, trigger auto-stop logic if needed
                    handleAutoStartStop("Still", System.currentTimeMillis());
                    // Optionally broadcast the change
                    // broadcastInstantaneousActivity("Still");
                } else {
                    Log.w(TAG_LATCH, "Timeout runnable executed, but latch was already cleared or start time reset.");
                }
            };
        }
        // Remove any previous posts and post the new one
        stillUnknownTimeoutHandler.removeCallbacks(stillUnknownTimeoutRunnable);
        stillUnknownTimeoutHandler.postDelayed(stillUnknownTimeoutRunnable, STILL_UNKNOWN_TIMEOUT_MS);
    }

    /** Manages auto-start and auto-stop timers based on detected activity. */
    private void handleAutoStartStop(String detectedMode, long currentTime) {
        boolean isMoving = detectedMode.equals("Walking") || detectedMode.equals("Bicycling") || detectedMode.equals("In Vehicle");
        boolean isStill = detectedMode.equals("Still");

        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        String trackingModePref = prefs.getString(MainActivity.KEY_TRACKING_MODE, MainActivity.MODE_AUTO);
        boolean isAutoMode = MainActivity.MODE_AUTO.equals(trackingModePref);

        if (isMoving) {
            // Cancel any pending auto-stop
            if (stopRunnable != null) {
                Log.d("AutoTrack_Service", "Movement detected (" + detectedMode + "), cancelling pending auto-stop.");
                stopDelayHandler.removeCallbacks(stopRunnable);
                stopRunnable = null;
            }
            // Handle Auto-Start
            if (isAutoMode && !isAutoTrackingCurrentlyActive) {
                // You might re-introduce the MIN_DURATION_FOR_AUTO_START_MS check here if needed
                Log.i("AutoTrack_Service", "Auto-Start Triggered by movement: " + detectedMode);
                startAutoTracking();
            }
        } else if (isStill) {
            // Handle Auto-Stop (Only if in Auto mode, tracking, AND NOT latched)
            if (isAutoMode && isAutoTrackingCurrentlyActive && latchedMode == null) { // Check if NOT latched
                if (stopRunnable == null) {
                    Log.w("AutoTrack_Service", "STILL detected (and not latched), scheduling auto-stop timer (" + AUTO_STOP_DELAY_MS + "ms).");
                    stopRunnable = this::stopAutoTracking; // Ensure stopAutoTracking method exists
                    stopDelayHandler.postDelayed(stopRunnable, AUTO_STOP_DELAY_MS);
                } else {
                    Log.d("AutoTrack_Service", "STILL detected (and not latched), auto-stop timer already scheduled.");
                }
            } else if (latchedMode != null) {
                Log.d(TAG_LATCH, "STILL detected, but currently latched. Auto-stop deferred.");
            }
        } else { // Unknown or other modes
            // Cancel pending auto-stop
            if (stopRunnable != null) {
                Log.d("AutoTrack_Service", "UNKNOWN detected, cancelling pending auto-stop.");
                stopDelayHandler.removeCallbacks(stopRunnable);
                stopRunnable = null;
            }
        }
    }

    /** Broadcasts the instantaneously detected mode for UI feedback. */
    private void broadcastInstantaneousActivity(String mode) {
        Intent activityIntent = new Intent(ACTION_ACTIVITY_DETECTED); // Ensure ACTION_ACTIVITY_DETECTED is defined
        activityIntent.putExtra(EXTRA_DETECTED_ACTIVITY_STRING, mode); // Ensure EXTRA_DETECTED_ACTIVITY_STRING is defined
        LocalBroadcastManager.getInstance(this).sendBroadcast(activityIntent);
    }


    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location location = locationResult.getLastLocation(); // Get the most recent location

                if (location != null) {
                    handleSpeedCheckForLatchTimeout(location);
                    float rawAccuracy = location.hasAccuracy() ? location.getAccuracy() : -1.0f;
                    Log.d("AccuracyDebug", "Service onLocationResult: Received Location with Accuracy = " + rawAccuracy);
                    Log.w("AutoTrack_Service", "SERVICE onLocationResult() received location.");

                    if (polylineManager == null) {
                        Log.e(TAG, "onLocationResult: PolylineManager is null!");
                        return;
                    }

                    int activityType = LocationTrackingService.latestActivityType;
                    int activityConfidence = LocationTrackingService.latestActivityConfidence;
                    float accuracy = location.hasAccuracy() ? location.getAccuracy() : -1.0f;

                    // --- Determine Mode to pass to PolylineManager ---
                    String modeForManager;
                    if (LocationTrackingService.this.currentOverrideMode != null) {
                        // Always prioritize user override
                        modeForManager = LocationTrackingService.this.currentOverrideMode;
                        Log.d(TAG, "onLocationResult: Using OVERRIDE Mode for PolylineManager: " + modeForManager);
                    } else {
                        // Use the debounced/filtered mode calculated and stored by handleActivityChange
                        modeForManager = LocationTrackingService.this.effectiveModeForPolyline; // <-- Use this variable
                        Log.d(TAG, "onLocationResult: Using Filtered/Debounced Mode for PolylineManager: " + modeForManager);
                    }
                    // --- End Mode Determination ---

                    // --- Call PolylineManager ---
                    // Pass the determined modeForManager (which incorporates override or debouncing)
                    String effectiveModeResult = polylineManager.processNewLocation(
                            location,
                            activityType,       // Keep passing raw type for logging/internal use if needed
                            activityConfidence, // Keep passing raw confidence
                            modeForManager,     // <<< PASS the CORRECT calculated mode HERE
                            accuracy
                    );

                    boolean isRecording = polylineManager.isRecordingActive();
                    Log.d(TAG, "onLocationResult: ModeForManager=" + modeForManager + ", RecordingActive=" + isRecording);

                    // --- Broadcast Update to MainActivity ---
                    Intent intent = new Intent(ACTION_LOCATION_BROADCAST);
                    intent.putExtra(EXTRA_LOCATION, location);
                    intent.putExtra(EXTRA_EFFECTIVE_MODE, modeForManager);
                    intent.putExtra(EXTRA_IS_RECORDING_ACTIVE, isRecording);

                    LatLng addedLatLng = polylineManager.getLastAddedPoint();
                    if (addedLatLng != null) {
                        intent.putExtra(EXTRA_NEW_GEOPOINT_LAT, addedLatLng.getLatitude());
                        intent.putExtra(EXTRA_NEW_GEOPOINT_LON, addedLatLng.getLongitude());
                    } else {
                        intent.putExtra(EXTRA_NEW_GEOPOINT_LAT, INVALID_LAT_LON);
                        intent.putExtra(EXTRA_NEW_GEOPOINT_LON, INVALID_LAT_LON);
                    }
                    LocalBroadcastManager.getInstance(LocationTrackingService.this).sendBroadcast(intent);

                } else {
                    Log.w("AutoTrack_Service", "SERVICE onLocationResult() - Location was null.");
                } // End if (location != null)
            } // End onLocationResult
        };
        Log.d(TAG, "LocationCallback created.");
    }

    /**
     * Checks speed during the Still/Unknown timeout phase to potentially reset the timer.
     */
    private void handleSpeedCheckForLatchTimeout(Location currentLocation) {
        // Only perform check if we are latched AND the timeout is potentially running
        if (latchedMode != null && stillUnknownStartTime > 0) {
            if (lastLocationForSpeed != null) {
                long timeDeltaMs = currentLocation.getTime() - lastLocationForSpeed.getTime();
                if (timeDeltaMs > 1000) { // Only calculate if time difference is reasonable (>1 sec)
                    float distanceMeters = currentLocation.distanceTo(lastLocationForSpeed);
                    float speedMs = distanceMeters / (timeDeltaMs / 1000.0f); // Speed in m/s

                    Log.d(TAG_LATCH, "Speed Check: LatchedMode=" + latchedMode + ", Current Speed=" + String.format("%.1f", speedMs) + " m/s");

                    boolean resetTimeout = false;
                    String reason = "";

                    // Check for inconsistency based on latched mode
                    if (latchedMode.equals("In Vehicle") && speedMs < MIN_SPEED_FOR_VEHICLE_RESET_MS) {
                        resetTimeout = true; // Too slow for vehicle
                        reason = "Speed too low for Vehicle";
                    } else if (latchedMode.equals("Bicycling") && speedMs > MAX_SPEED_FOR_BICYCLE_RESET_MS) {
                        resetTimeout = true; // Too fast for bicycle
                        reason = "Speed too high for Bicycling";
                    }
                    // Optional: Check if speed is *high* when expected to be Still/Unknown
                    else if (speedMs > MAX_SPEED_FOR_STILL_RESET_MS) {
                        // We detected Still/Unknown, but speed suggests movement. Reset timer.
                        resetTimeout = true;
                        reason = "Speed too high for Still/Unknown detection";
                    }

                    if (resetTimeout) {
                        Log.i(TAG_LATCH, "Resetting Still/Unknown timeout timer due to inconsistent speed. Reason: " + reason);
                        // Cancel existing and restart the timeout from NOW
                        stillUnknownTimeoutHandler.removeCallbacks(stillUnknownTimeoutRunnable);
                        stillUnknownStartTime = System.currentTimeMillis(); // Reset start time
                        // Ensure the runnable is initialized before posting
                        if (stillUnknownTimeoutRunnable == null) initializeAndStartLatchTimeout();
                        else stillUnknownTimeoutHandler.postDelayed(stillUnknownTimeoutRunnable, STILL_UNKNOWN_TIMEOUT_MS);
                    }
                }
            }
            // Update last location for next calculation
            lastLocationForSpeed = currentLocation;
        } else {
            // If not latched or timeout not running, clear the last location
            lastLocationForSpeed = null;
        }
    }

    private void stopTrackingUpdates() {
        // Helper method to stop FLP updates, called from onDestroy and stopAutoTracking
        if (fusedLocationClient != null && locationCallback != null) {
            Log.d(TAG,"Removing Fused Location Updates.");
            fusedLocationClient.removeLocationUpdates(locationCallback)
                    .addOnSuccessListener(aVoid -> Log.i(TAG, "Fused location updates removed successfully."))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to remove fused location updates.", e));
        } else {
            Log.d(TAG,"stopTrackingUpdates: FLP client or callback was null, nothing to remove.");
        }
    }

    private void startAutoTracking() {
        Log.w("StateSyncDebug", "SERVICE: startAutoTracking called. isAutoTrackingCurrentlyActive=" + isAutoTrackingCurrentlyActive);
        if (isAutoTrackingCurrentlyActive) return;
        // Permission check might be redundant if startTracking also checks, but safe to keep

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "startAutoTracking: Location permission missing!");
            return;
        }
        Log.i(TAG, "startAutoTracking: Starting location updates via FLP.");
        isAutoTrackingCurrentlyActive = true;
        startTracking(); // <<< This now calls the FLP version
        notifyTrackingStateChange(true);
    }


    // --- Ensure setupActivityUpdateReceiver is called in onCreate ---
    private void setupActivityUpdateReceiver() {
        activityUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_ACTIVITY_UPDATE.equals(intent.getAction())) {
                    int activityType = intent.getIntExtra(EXTRA_DETECTED_ACTIVITY_TYPE, DetectedActivity.UNKNOWN);
                    int confidence = intent.getIntExtra(EXTRA_DETECTED_ACTIVITY_CONFIDENCE, 0);
                    //float accuracy = location.hasAccuracy() ? location.getAccuracy() : -1.0f;
                    Log.d("AutoTrack_Service", "ActivityUpdateReceiver received update: Type=" + activityTypeToString(activityType) + ", Confidence=" + confidence);
                    handleActivityChange(activityType, confidence); // Process the change
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_ACTIVITY_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(activityUpdateReceiver, filter);
        Log.d(TAG, "Activity update receiver registered.");
    }


    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    private void removeActivityUpdates() {
        Log.d(TAG,"Removing activity updates for Service.");
        if (activityRecognitionClient != null) {
            try {
                activityRecognitionClient.removeActivityUpdates(getActivityDetectionPendingIntent())
                        .addOnSuccessListener(aVoid -> Log.d(TAG,"Service AR updates removed successfully."))
                        .addOnFailureListener(e -> Log.e(TAG,"Failed to remove service AR updates.", e));
            } catch (Exception e) { Log.e(TAG,"Exception removing service AR updates.", e); }
        }
    }

    // Gets the PendingIntent for the Activity Recognition BroadcastReceiver
    // IMPORTANT: The receiver will update the STATIC variables in THIS service class.
    private  PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, DetectedActivitiesBroadcastReceiver.class);

        // Change Request Code to match snippet (optional, but let's try it)
        int requestCode = 0; // Changed from 123

        // Change Flags to match snippet (MUTABLE)
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE; // Using MUTABLE

        // ** IMPORTANT: Update the log message to reflect the change! **
        Log.d("AutoTrack_Service", "Creating PendingIntent for AR. RequestCode=" + requestCode + ", Flags=FLAG_UPDATE_CURRENT | FLAG_MUTABLE"); // UPDATED LOG

        return PendingIntent.getBroadcast(this, requestCode, intent, flags);
    }



    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    @Override
    public void onDestroy() {
        Log.e(TAG, "SERVICE ONDESTROY CALLED!");
        stopTrackingUpdates(); // Stop FLP updates

        // --- Stop Fused Location Updates ---
        if (fusedLocationClient != null && locationCallback != null) {
            Log.d(TAG,"Removing Fused Location Updates in onDestroy.");
            fusedLocationClient.removeLocationUpdates(locationCallback)
                    .addOnSuccessListener(aVoid -> Log.i(TAG, "onDestroy: Fused location updates removed successfully."))
                    .addOnFailureListener(e -> Log.e(TAG, "onDestroy: Failed to remove fused location updates.", e));
        }

        if (overrideReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(overrideReceiver);
            Log.d(TAG, "Override receiver unregistered.");
            overrideReceiver = null;
        }

        if (activityUpdateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(activityUpdateReceiver);
            Log.d(TAG, "Activity Update receiver unregistered.");
            activityUpdateReceiver = null;
        }
        if (stopRunnable != null) {
            stopDelayHandler.removeCallbacks(stopRunnable);
            stopRunnable = null;
            Log.d(TAG, "Cancelled pending auto-stop on destroy.");
        }

        if (stillUnknownTimeoutRunnable != null && stillUnknownTimeoutHandler != null) {
            stillUnknownTimeoutHandler.removeCallbacks(stillUnknownTimeoutRunnable);
            stillUnknownTimeoutRunnable = null; // Optional: clear runnable ref
            Log.d(TAG_LATCH, "Cancelled latch timeout on destroy.");
        }

        long lastSegmentStartTime = -1;
        if (polylineManager != null && polylineManager.getCurrentSegmentPoints() != null && !polylineManager.getCurrentSegmentPoints().isEmpty()) {
            // Get the start time *before* finalizing, as finalize clears the current segment
            lastSegmentStartTime = polylineManager.getCurrentSegmentPoints().get(0).timestamp;
        }

        // Finalize last segment via manager BEFORE stopping executor
        if (polylineManager != null) {
            Log.d(TAG, "Finalizing last segment in Service onDestroy...");
            polylineManager.finalizeAndSaveCurrentSegment();

            // After saving, send the broadcast
            Intent newJourneyIntent = new Intent(ACTION_NEW_JOURNEY_SAVED);
            if (lastSegmentStartTime != -1) {
                newJourneyIntent.putExtra(EXTRA_NEW_JOURNEY_START_TIME, lastSegmentStartTime);
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(newJourneyIntent);
            Log.i(TAG, "Broadcast ACTION_NEW_JOURNEY_SAVED sent from onDestroy.");
        } else {
            Log.w(TAG,"PolylineManager was null in onDestroy.");
        }

        if (activityRecognitionClient != null) removeActivityUpdates(); // Ensure removeActivityUpdates exists

        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
            Log.d(TAG, "Background executor shut down.");
        }

        stopForeground(true); // Use true to remove notification immediately
        super.onDestroy(); // Call super class's onDestroy
    }

    private void stopAutoTracking() {
        Log.w(TAG_SYNC, "SERVICE: stopAutoTracking called. isAutoTrackingCurrentlyActive=" + isAutoTrackingCurrentlyActive);
        if (!isAutoTrackingCurrentlyActive) return;

        long lastSegmentStartTime = -1; // To store the start time of the segment being finalized
        if (polylineManager != null && polylineManager.getCurrentSegmentPoints() != null && !polylineManager.getCurrentSegmentPoints().isEmpty()) {
            lastSegmentStartTime = polylineManager.getCurrentSegmentPoints().get(0).timestamp;
        }

        isAutoTrackingCurrentlyActive = false;
        stopRunnable = null; // Clear the runnable

        clearLatchAndTimeout(); // Clear any active latch or timeout

        stopTrackingUpdates(); // Stop FLP updates

        if (polylineManager != null) {
            polylineManager.finalizeAndSaveCurrentSegment();
            Log.d(TAG, "stopAutoTracking: Called finalizeAndSaveCurrentSegment.");

            // After saving, send the broadcast
            Intent newJourneyIntent = new Intent(ACTION_NEW_JOURNEY_SAVED);
            if (lastSegmentStartTime != -1) {
                newJourneyIntent.putExtra(EXTRA_NEW_JOURNEY_START_TIME, lastSegmentStartTime);
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(newJourneyIntent);
            Log.i(TAG, "Broadcast ACTION_NEW_JOURNEY_SAVED sent from stopAutoTracking.");
        }
        notifyTrackingStateChange(false); // Notify that general tracking has stopped
    }



    private void setupOverrideReceiver() {
        overrideReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Check if the received action is the one we expect
                if (MainActivity.ACTION_MODE_OVERRIDE.equals(intent.getAction())) {
                    currentOverrideMode = intent.getStringExtra(MainActivity.EXTRA_OVERRIDE_MODE);
                    Log.i(TAG, "Received mode override broadcast: New Override Mode = " + currentOverrideMode);

                    // If PolylineManager exists, force it to start a new segment
                    if (polylineManager != null) {
                        Location lastKnownLocation = null; // <<< Pass null

                        // Determine the mode to start the *new* segment with.
                        // If override is cleared (null), use "Unknown" or try to auto-detect?
                        // Let's use "Unknown" if cleared, otherwise use the override mode.
                        String modeForNewSegment = (currentOverrideMode != null) ? currentOverrideMode : "Unknown";

                        Log.d(TAG,"Telling PolylineManager to force a new segment. Mode: " + modeForNewSegment);
                        polylineManager.forceNewSegment(modeForNewSegment, lastKnownLocation);
                    } else {
                        Log.e(TAG, "PolylineManager is null, cannot force new segment on override.");
                    }
                }
            }
        };
        // Create an IntentFilter for the specific action sent by MainActivity
        IntentFilter filter = new IntentFilter(MainActivity.ACTION_MODE_OVERRIDE);
        // Register the receiver using LocalBroadcastManager
        LocalBroadcastManager.getInstance(this).registerReceiver(overrideReceiver, filter);
        Log.d(TAG, "Override receiver registered.");
    }

    public class LocalBinder extends Binder {
        LocationTrackingService getService() {
            // Return this instance of LocationTrackingService so clients can call public methods
            return LocationTrackingService.this;
        }
    }

    /**
     * Returns the current tracking state managed by the service.
     * NOTE: Adapt this logic if manual mode tracking state is stored differently.
     * @return true if the service considers tracking to be active, false otherwise.
     */
    public boolean isCurrentlyTracking() {
        // Assuming isAutoTrackingCurrentlyActive reflects the relevant state.
        // If manual tracking uses a different mechanism controlled *only* by the Activity,
        // this logic might need adjustment. For now, let's use this flag.
        Log.d(TAG, "isCurrentlyTracking() called. Returning: " + this.isAutoTrackingCurrentlyActive);
        return this.isAutoTrackingCurrentlyActive; // Or return a combination of flags if needed
    }

// Make sure isAutoTrackingCurrentlyActive is accurately maintained
// in startAutoTracking() and stopAutoTracking()

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

}