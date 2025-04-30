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

        // Initialize Activity Recognition Client here too
        initializeActivityRecognition();

        // Modify the PolylineManager creation to pass the handler
        Log.d(TAG, "Creating PolylineManager instance.");
        polylineManager = new PolylineManager(getApplicationContext(), backgroundExecutor, gson, mainThreadHandler); // Pass the handler
        setupOverrideReceiver();
        setupActivityUpdateReceiver();
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

    // Inside LocationTrackingService.java

    private void handleActivityChange(int activityType, int confidence) {
        Log.d("ActivityDebounce", "--- handleActivityChange START ---");
        Log.d("ActivityDebounce", "Raw Detection: Type=" + activityTypeToString(activityType) + ", Conf=" + confidence);

        // --- Step 1: Determine the *instantaneously* detected mode string ---
        String detectedModeNow = "Unknown"; // Default
        // (Use your existing confidence thresholds here)
        if (activityType == DetectedActivity.WALKING && confidence > WALKING_ACTIVITY_CONFIDENCE_THRESHOLD) {
            detectedModeNow = "Walking";
        } else if (activityType == DetectedActivity.ON_BICYCLE && confidence > BICYCLING_ACTIVITY_CONFIDENCE_THRESHOLD) {
            detectedModeNow = "Bicycling";
        } else if (activityType == DetectedActivity.IN_VEHICLE && confidence > IN_VEHICLE_ACTIVITY_CONFIDENCE_THRESHOLD) {
            detectedModeNow = "In Vehicle";
        } else if (activityType == DetectedActivity.STILL && confidence > STILL_CONFIDENCE_THRESHOLD) {
            detectedModeNow = "Still";
        }
        // Otherwise, it remains "Unknown"

        Log.d("ActivityDebounce", "Instantaneous Mode Determined: " + detectedModeNow);

        // --- Step 2: Broadcast Instantaneous Mode for UI Feedback ---
        // (Send this regardless of debouncing for immediate user feedback)
        Intent activityIntent = new Intent(ACTION_ACTIVITY_DETECTED);
        activityIntent.putExtra(EXTRA_DETECTED_ACTIVITY_STRING, detectedModeNow);
        LocalBroadcastManager.getInstance(this).sendBroadcast(activityIntent);
        Log.d("ActivityDebounce", "Broadcasted ACTION_ACTIVITY_DETECTED: " + detectedModeNow);


        // --- Step 3: Apply Debouncing/Filtering for Auto-Start/Stop and Segment Mode ---
        long currentTime = System.currentTimeMillis();
        String effectiveModeForManager = lastConfirmedMode; // Start with the last confirmed mode

        // Check if the new detection requires starting a potential mode change timer
        boolean isMovingNow = detectedModeNow.equals("Walking") || detectedModeNow.equals("Bicycling") ||
                detectedModeNow.equals("In Vehicle");
        boolean isStillOrUnknownNow = detectedModeNow.equals("Still") || detectedModeNow.equals("Unknown");

        if (isMovingNow) {
            // --- Handle Potential Movement Start/Change ---
            if (detectedModeNow.equals(potentialNextMode)) {
                // Continue detecting the same potential mode
                long durationDetected = currentTime - potentialModeStartTime;
                Log.d("ActivityDebounce", "Continuing potential mode '" + potentialNextMode + "' for " + durationDetected + "ms");

                // Check if duration is long enough to confirm the mode change for segments
                if (durationDetected >= MIN_DURATION_FOR_MODE_CHANGE_MS) {
                    Log.i("ActivityDebounce", "CONFIRMING Mode Change to: " + potentialNextMode);
                    lastConfirmedMode = potentialNextMode;
                    lastConfirmedModeTime = currentTime;
                    effectiveModeForManager = lastConfirmedMode;
                    potentialNextMode = null; // Reset potential mode tracker
                    potentialModeStartTime = 0;
                } else {
                    // Not long enough to confirm for segments yet, keep last confirmed mode for manager
                    effectiveModeForManager = lastConfirmedMode;
                    Log.d("ActivityDebounce", "Mode '" + potentialNextMode + "' detected but duration < " + MIN_DURATION_FOR_MODE_CHANGE_MS + "ms. Using last confirmed '" + lastConfirmedMode + "' for manager.");
                }

                // Check if duration is long enough for AUTO-START (independent of segment mode confirmation)
                if (!isAutoTrackingCurrentlyActive && durationDetected >= MIN_DURATION_FOR_AUTO_START_MS) {
                    Log.i("ActivityDebounce", "CONFIRMING Auto-Start Trigger (Mode: " + potentialNextMode + ")");
                    // Perform auto-start if in auto mode
                    SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
                    String trackingMode = prefs.getString(MainActivity.KEY_TRACKING_MODE, MainActivity.MODE_AUTO);
                    if (MainActivity.MODE_AUTO.equals(trackingMode)) {
                        startAutoTracking(); // Call the start method
                    }
                }

            } else {
                // New potential mode detected, start the timer
                Log.d("ActivityDebounce", "NEW Potential Mode detected: " + detectedModeNow + ". Starting timer.");
                potentialNextMode = detectedModeNow;
                potentialModeStartTime = currentTime;
                // Keep using the last confirmed mode until the new one is confirmed
                effectiveModeForManager = lastConfirmedMode;
            }
            // If moving, cancel any pending auto-stop
            if (stopRunnable != null) {
                Log.d("ActivityDebounce", "Movement detected (" + detectedModeNow + "), cancelling pending auto-stop.");
                stopDelayHandler.removeCallbacks(stopRunnable);
                stopRunnable = null;
            }

        } else if (isStillOrUnknownNow) {
            // --- Handle Still or Unknown ---
            Log.d("ActivityDebounce", "Detected Still/Unknown. Resetting potential mode timer.");
            potentialNextMode = null; // Reset potential mode if we stop moving
            potentialModeStartTime = 0;

            long timeSinceLastValid = currentTime - lastConfirmedModeTime;
            Log.d("ActivityDebounce", "Time since last confirmed mode '" + lastConfirmedMode + "': " + timeSinceLastValid + "ms");

            // Keep using the last valid mode for a short duration?
            if (lastConfirmedModeTime > 0 && timeSinceLastValid < MAX_DURATION_TO_KEEP_LAST_MODE_MS) {
                effectiveModeForManager = lastConfirmedMode; // Override Still/Unknown for manager
            } else {
                // Timeout expired or no previous valid mode, use the actual Still/Unknown
                effectiveModeForManager = detectedModeNow;
                lastConfirmedMode = effectiveModeForManager; // Update confirmed mode to Still/Unknown
                lastConfirmedModeTime = currentTime;
                Log.d("ActivityDebounce", "Using ACTUAL mode '" + effectiveModeForManager + "' for manager (Timeout expired or no previous valid)");
            }

            // Handle Auto-Stop logic (only if detected mode is actually STILL)
            if (detectedModeNow.equals("Still")) {
                SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
                String trackingMode = prefs.getString(MainActivity.KEY_TRACKING_MODE, MainActivity.MODE_AUTO);
                if (MainActivity.MODE_AUTO.equals(trackingMode) && isAutoTrackingCurrentlyActive) {
                    if (stopRunnable == null) {
                        Log.w("ActivityDebounce", "STILL detected, scheduling auto-stop timer (" + AUTO_STOP_DELAY_MS + "ms).");
                        stopRunnable = this::stopAutoTracking;
                        stopDelayHandler.postDelayed(stopRunnable, AUTO_STOP_DELAY_MS);
                    } else {
                        Log.d("ActivityDebounce", "STILL detected, auto-stop timer already scheduled.");
                    }
                }
            } else {
                // If Unknown, ensure any pending stop is cancelled
                if (stopRunnable != null) {
                    Log.d("ActivityDebounce", "UNKNOWN detected, cancelling pending auto-stop.");
                    stopDelayHandler.removeCallbacks(stopRunnable);
                    stopRunnable = null;
                }
            }

        } else {
            // Should not happen if logic above is complete, but handles unexpected cases
            Log.w("ActivityDebounce", "Unhandled activity type in debouncing logic: " + activityTypeToString(activityType));
            potentialNextMode = null;
            potentialModeStartTime = 0;
            effectiveModeForManager = "Unknown"; // Fallback
            lastConfirmedMode = effectiveModeForManager;
            lastConfirmedModeTime = currentTime;
            // Cancel pending stop if any
            if (stopRunnable != null) {
                stopDelayHandler.removeCallbacks(stopRunnable);
                stopRunnable = null;
            }
        }

        // --- Step 4: Update PolylineManager with the *effective* mode ---
        // This part now happens inside onLocationResult using the effectiveModeForManager
        // We store it in a member variable to be accessed by onLocationResult
        // (Alternatively, pass it directly if handleActivityChange is called FROM onLocationResult)
        // For now, let's assume onLocationResult will access 'lastConfirmedMode' or calculate the effective mode itself based on timestamps.
        // Let's simplify: we'll determine the mode to *pass* to polylineManager here.

        String modeForPolylineManager;
        if(isStillOrUnknownNow && lastConfirmedModeTime > 0 && (currentTime - lastConfirmedModeTime < MAX_DURATION_TO_KEEP_LAST_MODE_MS)) {
            modeForPolylineManager = lastConfirmedMode; // Use held mode
        } else {
            modeForPolylineManager = lastConfirmedMode; // Use the currently confirmed mode (which might be Still/Unknown now)
        }

        Log.d("ActivityDebounce", "Final Effective Mode for PolylineManager (this cycle): " + modeForPolylineManager);

        // Store this decision for onLocationResult to use (add a new member variable if needed)
        // private String effectiveModeForPolyline = "Unknown"; // Add this member variable
        this.effectiveModeForPolyline = modeForPolylineManager; // Update it here

        Log.d("ActivityDebounce", "--- handleActivityChange END ---");
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location location = locationResult.getLastLocation(); // Get the most recent location

                if (location != null) {
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
                        modeForManager = LocationTrackingService.this.effectiveModeForPolyline; // <<< CORRECTED: Directly use the member variable
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
                    // Broadcast the mode that was actually used by the manager
                    intent.putExtra(EXTRA_EFFECTIVE_MODE, modeForManager);
                    // Use the declared isRecording variable
                    intent.putExtra(EXTRA_IS_RECORDING_ACTIVE, isRecording); // <<< Now uses declared variable

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


        // Finalize last segment via manager BEFORE stopping executor
        if (polylineManager != null) {
            Log.d(TAG, "Finalizing last segment in Service onDestroy...");
            polylineManager.finalizeAndSaveCurrentSegment();
        } else {
            Log.w(TAG,"PolylineManager was null in onDestroy.");
        }

        removeActivityUpdates(); // Stop activity recognition

        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
            Log.d(TAG, "Background executor shut down.");
        }

        stopForeground(true); // Use true to remove notification immediately
        Log.d(TAG, "Service stopped foreground.");
        super.onDestroy(); // Call super class's onDestroy
    }

    private void stopAutoTracking() {
        Log.w("StateSyncDebug", "SERVICE: stopAutoTracking called. isAutoTrackingCurrentlyActive=" + isAutoTrackingCurrentlyActive);
        if (!isAutoTrackingCurrentlyActive) return;
        isAutoTrackingCurrentlyActive = false;
        stopRunnable = null;
        stopTrackingUpdates(); // Stop FLP updates
        if (polylineManager != null) polylineManager.finalizeAndSaveCurrentSegment();
        notifyTrackingStateChange(false);
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