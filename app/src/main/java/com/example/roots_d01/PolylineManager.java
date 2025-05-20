package com.example.roots_d01;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.gms.location.DetectedActivity; // For constants
import com.google.gson.Gson;

import org.maplibre.android.geometry.LatLng;

import java.io.File; // For saving logic later
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

// Class responsible for managing polyline segment data, filtering, and saving
public class PolylineManager {

    private static final String TAG = "PolylineManager";

    // --- Dependencies (Passed in Constructor) ---
    private final Context appContext; // Use Application context to avoid leaks
    private final ExecutorService backgroundExecutor;
    private final Gson gson;
    // private final Handler mainThreadHandler; // May not be needed directly here

    // --- State Variables (Managed by this class) ---
    private List<PolylinePoint> currentPolylinePoints; // Points for the segment being built
    private long lastLocationTimestamp; // Timestamp of the last processed location
    private long lastPolylinePointTimeAdded; // Timestamp of the last point actually added
    private String currentSegmentDominantMode; // Dominant mode for the current segment
    private long lastSaveTime; // Timestamp of the last successful save trigger

    // --- Constants (Consider making these configurable/passed in) ---
    private static final long SAVE_COOLDOWN = 5000; // 5 seconds ms
    private static final long MIN_TIME_INTERVAL = 3000; // Base time interval ms
    private static final float MIN_MOVEMENT_THRESHOLD = 3.0f; // Base movement threshold meters
    private static final long DEFAULT_SESSION_TIMEOUT = 15000; // 15 seconds ms
    private static final long IN_VEHICLE_SESSION_TIMEOUT = 45000; // 45 seconds ms
    private static final float POOR_ACCURACY_THRESHOLD = 35.0f; // Meters
    private final Handler mainThreadHandler;

    private boolean isWaitingForInitialDistance = false; // NEW: Flag to indicate waiting state
    private Location segmentStartLocation = null;        // NEW: Location where the segment started
    private float initialDistanceThreshold; // In metres
    private boolean isRecordingActiveForCurrentSegment = false;
    private static final float MAX_ALLOWED_ACCURACY = 50.0f; // e.g., Discard anything worse than 50m accuracy
    public static final float SKIP_MATCHING_ACCURACY_THRESHOLD = 15.0f; // Your threshold (meters) 80% of points must be more accurate than this value to skip matching
    private static final String TAG_MATCH_CHECK = "MapMatchCheck";
    private final AtomicBoolean isSavePending = new AtomicBoolean(false); // Add this
    private float initialDistanceThresholdMeters;
    private int accuratePointsAfterInitialDistanceCount = 0;
    private static final int MIN_ACCURATE_POINTS_FOR_STABLE_LOCK = 3; // Configurable: e.g., 3 points
    private static final float ACCURACY_THRESHOLD_FOR_STABLE_LOCK = 20.0f; // Configurable: e.g., points must be < 20m accuracy
    private static final float WALKING_MOVEMENT_THRESHOLD = 2.0f;   // e.g., 2 meters for walking
    private static final float BICYCLING_MOVEMENT_THRESHOLD = 4.0f; // e.g., 4 meters for bicycling
    private static final float IN_VEHICLE_MOVEMENT_THRESHOLD = 8.0f; // e.g., 8 meters for in-vehicle
    // MIN_MOVEMENT_THRESHOLD (3.0f) can remain as a default or for "Unknown" activity
    private static final int ACCURACY_WINDOW_SIZE = 5; // Number of recent accuracies to consider
    private final List<Float> recentAccuracies = new ArrayList<>();
    private static final float ACCEPTABLE_ACCURACY_DEGRADATION_FACTOR = 2.0f; // Allow new accuracy up to X times worse than recent average
    private static final float SIGNIFICANT_ACCURACY_DEGRADATION_THRESHOLD = 30.0f; // If recent average is already bad (e.g. >30m), be less tolerant.

    // Constructor
    public PolylineManager(Context context, ExecutorService executor, Gson gsonInstance, Handler handler) { // <<< ADDED Handler handler parameter
        this.appContext = context.getApplicationContext();
        this.backgroundExecutor = executor;
        this.gson = gsonInstance;
        this.mainThreadHandler = handler; // <<< Now 'handler' exists and is assigned

        SharedPreferences prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        initialDistanceThresholdMeters = prefs.getInt(
                SettingsActivity.KEY_INITIAL_RECORDING_DISTANCE_METERS,
                SettingsActivity.DEFAULT_INITIAL_DISTANCE
        );
        // Log.d(TAG, "PolylineManager loaded Initial Distance Threshold: " + this.initialDistanceThresholdMeters + "m"); // Corrected variable name if initialDistanceThreshold was a typo
        Log.d(TAG, "PolylineManager loaded Initial Distance Threshold: " + this.initialDistanceThresholdMeters + "m");

        initializeNewSegmentInternal("Unknown", null, false);
        Log.d(TAG, "PolylineManager Initialized");
    }


    public synchronized String processNewLocation(Location location, int activityType, int activityConfidence, @Nullable String serviceDeterminedMode, float accuracy) {
        if (location == null) {
            return this.currentSegmentDominantMode != null ? this.currentSegmentDominantMode : "Unknown";
        }

        Log.d("AccuracyDebug", "PolylineManager processNewLocation: Received accuracy = " + accuracy);

        long currentTime = System.currentTimeMillis();

// --- Adaptive Accuracy Check with Rolling Window ---
        boolean adaptivelyAcceptPoint = true; // Assume acceptable by default, prove otherwise
        if (location.hasAccuracy()) {
            float currentAccuracy = location.getAccuracy();

            // 1. Hard reject if accuracy exceeds the absolute maximum
            if (currentAccuracy > MAX_ALLOWED_ACCURACY) {
                Log.w(TAG, "processNewLocation: Discarding point due to very poor accuracy (hard limit): " + currentAccuracy + "m");
                this.lastLocationTimestamp = currentTime; // Still update timestamp
                return this.currentSegmentDominantMode != null ? this.currentSegmentDominantMode : "Unknown";
            }

            // 2. If accuracy is good (e.g., better than POOR_ACCURACY_THRESHOLD), it's generally acceptable
            if (currentAccuracy <= POOR_ACCURACY_THRESHOLD) {
                // adaptivelyAcceptPoint remains true, point is good.
                // Add to recent accuracies later if it passes other filters (movement, etc.)
            } else {
                // Accuracy is between POOR_ACCURACY_THRESHOLD and MAX_ALLOWED_ACCURACY.
                // This is where the rolling window logic applies.
                if (recentAccuracies.isEmpty()) {
                    // No history, accept this point if it's within MAX_ALLOWED_ACCURACY (which it is if we reached here)
                    adaptivelyAcceptPoint = true;
                    Log.d(TAG, "Adaptive Accuracy: No recent history, accepting point with accuracy: " + currentAccuracy + "m");
                } else {
                    float sumOfRecentAccuracies = 0;
                    for (float acc : recentAccuracies) {
                        sumOfRecentAccuracies += acc;
                    }
                    float averageRecentAccuracy = sumOfRecentAccuracies / recentAccuracies.size();

                    Log.d(TAG, "Adaptive Accuracy: Current=" + String.format("%.1f", currentAccuracy) +
                            "m, RecentAvg=" + String.format("%.1f", averageRecentAccuracy) +
                            "m (WindowSize=" + recentAccuracies.size() + ")");

                    // Condition 1: Is the new accuracy too much worse than the recent average?
                    boolean muchWorseThanAverage = currentAccuracy > (averageRecentAccuracy * ACCEPTABLE_ACCURACY_DEGRADATION_FACTOR);

                    // Condition 2: Is the recent average already quite bad, and this point makes it even worse?
                    boolean averageIsAlreadyPoorAndGettingWorse = averageRecentAccuracy > SIGNIFICANT_ACCURACY_DEGRADATION_THRESHOLD && currentAccuracy > averageRecentAccuracy;

                    if (muchWorseThanAverage || averageIsAlreadyPoorAndGettingWorse) {
                        Log.w(TAG, "Adaptive Accuracy: Discarding point. Current: " + String.format("%.1f", currentAccuracy) +
                                "m. MuchWorse: " + muchWorseThanAverage +
                                " (RecentAvg: " + String.format("%.1f", averageRecentAccuracy) +
                                ", Factor: " + ACCEPTABLE_ACCURACY_DEGRADATION_FACTOR + ")" +
                                ", AvgPoorAndWorsening: " + averageIsAlreadyPoorAndGettingWorse +
                                " (RecentAvgThreshold: " + SIGNIFICANT_ACCURACY_DEGRADATION_THRESHOLD + ")");
                        adaptivelyAcceptPoint = false;
                    } else {
                        // Point is acceptable based on adaptive logic
                        Log.d(TAG, "Adaptive Accuracy: Accepting point with accuracy: " + currentAccuracy + "m (within adaptive tolerance).");
                    }
                }
            }
        } else { // No accuracy information
            Log.w(TAG, "processNewLocation: Discarding point due to missing accuracy information.");
            adaptivelyAcceptPoint = false; // If no accuracy, we can't assess it.
        }

        if (!adaptivelyAcceptPoint) {
            this.lastLocationTimestamp = currentTime; // Update timestamp even if discarded
            return this.currentSegmentDominantMode != null ? this.currentSegmentDominantMode : "Unknown";
        }

        // --- Determine Effective Mode (Trust Service or Override) ---
        String effectiveMode = (serviceDeterminedMode != null) ? serviceDeterminedMode : "Unknown"; // Use passed mode, default to Unknown if null

        // --- Session Management Checks ---
        // Use the 'effectiveMode' determined above
        long currentSessionTimeout = getSessionTimeoutForMode(effectiveMode);
        boolean newSessionStarted = false;

        if (this.lastLocationTimestamp != 0 && (currentTime - this.lastLocationTimestamp) > currentSessionTimeout) {
            Log.i(TAG, "Session timeout detected (" + (currentTime - this.lastLocationTimestamp) + "ms > " + currentSessionTimeout + "ms). Starting new segment. Mode: " + effectiveMode);
            // *** Update dominant mode WHEN starting a new segment ***
            this.currentSegmentDominantMode = effectiveMode;
            startNewSegment(effectiveMode, location);
            newSessionStarted = true;
        }

        // Check for session timeout based on last *processed* location
        if (this.lastLocationTimestamp != 0 && (currentTime - this.lastLocationTimestamp) > currentSessionTimeout) {
            Log.i(TAG, "Session timeout detected (" + (currentTime - this.lastLocationTimestamp) + "ms > " + currentSessionTimeout + "ms). Starting new segment. Mode: " + effectiveMode);
            startNewSegment(effectiveMode, location); // Pass the mode determined above
            newSessionStarted = true;
        }
        // Check for mode change if not overridden and not a new session
        else if (this.currentPolylinePoints != null && !this.currentPolylinePoints.isEmpty() && !newSessionStarted) {
            // Compare against the mode of the last *added* point
            String lastRecordedMode = this.currentPolylinePoints.get(this.currentPolylinePoints.size() - 1).transportMode;
            // Start new segment if mode changes *to* a recognized active mode from another mode
            boolean changingToActive = (effectiveMode.equals("Walking") || effectiveMode.equals("Bicycling") || effectiveMode.equals("In Vehicle"));
            boolean changingFromDifferent = !effectiveMode.equals(lastRecordedMode);

            if (changingToActive && changingFromDifferent && !effectiveMode.equals("Unknown") && !effectiveMode.equals("Still")) {
                Log.i(TAG, "Mode change detected (" + lastRecordedMode + "->" + effectiveMode + "). Starting new segment.");
                startNewSegment(effectiveMode, location); // Pass the mode determined above
                newSessionStarted = true;
            }
        }
        // --- End Session Management ---


// --- Check Initial Distance Threshold ---
        if (this.isWaitingForInitialDistance) {
            if (this.segmentStartLocation != null) {
                float distanceSinceStart = location.distanceTo(this.segmentStartLocation);
                if (distanceSinceStart >= this.initialDistanceThresholdMeters) {
                    Log.d(TAG, "Initial " + this.initialDistanceThresholdMeters + "m distance threshold met.");
                    // Now, check for stable lock
                    if (location.hasAccuracy() && location.getAccuracy() <= ACCURACY_THRESHOLD_FOR_STABLE_LOCK) {
                        accuratePointsAfterInitialDistanceCount++;
                        Log.d(TAG, "Accurate point (" + location.getAccuracy() + "m) received after distance threshold. Count: " + accuratePointsAfterInitialDistanceCount);
                        if (accuratePointsAfterInitialDistanceCount >= MIN_ACCURATE_POINTS_FOR_STABLE_LOCK) {
                            Log.i(TAG, "Stable GPS lock achieved (" + accuratePointsAfterInitialDistanceCount + " accurate points). Enabling recording for segment.");
                            this.isWaitingForInitialDistance = false;
                            this.isRecordingActiveForCurrentSegment = true; // <<< Enable recording HERE
                            // Optionally, add the current point here if it meets other criteria,
                            // or let it be added in the subsequent point filtering logic.
                            // For simplicity, we'll let the main logic add it.
                        }
                    } else {
                        // Accurate point not received, reset counter if you want them to be consecutive
                        Log.d(TAG, "Point received after distance threshold, but accuracy (" + (location.hasAccuracy() ? location.getAccuracy() : "N/A") + "m) is not sufficient for stable lock. Resetting accurate count.");
                        accuratePointsAfterInitialDistanceCount = 0;
                    }
                } else {
                    Log.d(TAG, "Waiting for initial " + this.initialDistanceThresholdMeters + "m distance (Current: " + String.format("%.1f", distanceSinceStart) + "m). Skipping point recording phase.");
                    // accuratePointsAfterInitialDistanceCount = 0; // Reset if distance not met yet
                }
            } else { // Not waiting for initial distance (either threshold met or was not applicable)
                // This case implies segmentStartLocation might be null or distance threshold was 0.
                // If it wasn't waiting, isRecordingActiveForCurrentSegment would have been set in initializeNewSegmentInternal.
            }
            // Update timestamp AFTER initial checks, but before returning if still waiting
            if (this.isWaitingForInitialDistance) { // If still waiting after checks
                this.lastLocationTimestamp = currentTime;
            }
        }

        // --- Determine Min Movement Threshold based on Effective Mode ---
        float currentMinMovementThreshold; // Use a local variable for this specific point check
        switch (effectiveMode) {
            case "Walking":
                currentMinMovementThreshold = WALKING_MOVEMENT_THRESHOLD;
                break;
            case "Bicycling":
                currentMinMovementThreshold = BICYCLING_MOVEMENT_THRESHOLD;
                break;
            case "In Vehicle":
                currentMinMovementThreshold = IN_VEHICLE_MOVEMENT_THRESHOLD;
                break;
            default: // Still, Unknown, or any other mode
                currentMinMovementThreshold = MIN_MOVEMENT_THRESHOLD; // Default threshold
                break;
        }
// Log the chosen threshold for debugging
        Log.d(TAG, "Using movement threshold: " + currentMinMovementThreshold + "m for mode: " + effectiveMode);
// --- End Determine Min Movement Threshold ---



        // --- Point Filtering (Time/Distance) ---
// This check is independent of activity type, purely based on GPS movement
        boolean likelyIndoorsOrTiltingOrStill = false;
        if (location.hasAccuracy() && location.getAccuracy() > POOR_ACCURACY_THRESHOLD) {
            likelyIndoorsOrTiltingOrStill = true;
            Log.d(TAG, "Poor GPS Accuracy detected (" + location.getAccuracy() + "m), potentially affecting movement threshold logic.");
        }
        // Check activity type directly here if needed, but effectiveMode is checked later
        // if ((activityType == DetectedActivity.TILTING || activityType == DetectedActivity.STILL) && activityConfidence > 50) { likelyIndoorsOrTiltingOrStill = true; Log.d(TAG,"Detected TILTING or STILL"); }



        long currentMinTimeInterval = getMinTimeIntervalForMode(effectiveMode);
        boolean shouldAddBasedOnGPS = shouldAddPoint(location, currentTime, currentMinTimeInterval, currentMinMovementThreshold);
        // --- End Point Filtering ---


        // --- Add Point if Applicable ---
        // Condition: Must pass GPS filter, not a new segment, segment recording active, AND current mode must be an active one
        boolean isActiveMovementMode = !(effectiveMode.equals("Still") || effectiveMode.equals("Unknown")); // Excludes Still and Unknown explicitly


        Log.d(TAG, "processNewLocation: Checking final add conditions: shouldAddBasedOnGPS=" + shouldAddBasedOnGPS +
                ", newSessionStarted=" + newSessionStarted +
                ", isRecordingActiveForCurrentSegment=" + this.isRecordingActiveForCurrentSegment +
                ", isActiveMovementMode=" + isActiveMovementMode);

        if (shouldAddBasedOnGPS && !newSessionStarted && this.isRecordingActiveForCurrentSegment && isActiveMovementMode) { // Added isActiveMovementMode check
            if (this.currentPolylinePoints == null) {
                Log.e(TAG, "CRITICAL: currentPolylinePoints list is null! Reinitializing segment.");
                initializeNewSegmentInternal(effectiveMode, location, false);            }
            // Check again just in case initialize failed
            if (this.currentPolylinePoints != null) {
                Log.d("AccuracyDebug", "PolylineManager processNewLocation: ADDING point with accuracy = " + accuracy);
                // Add the point with the determined effectiveMode for this location instance
                this.currentPolylinePoints.add(new PolylinePoint(
                        location.getLatitude(),
                        location.getLongitude(),
                        currentTime,
                        effectiveMode,
                        accuracy));
                this.lastPolylinePointTimeAdded = currentTime; // Update time of *last added point*
                Log.i(TAG, ">>> POINT ADDED to PolylineManager list. Mode: " + effectiveMode + ", Accuracy: " + accuracy + ", New Size: " + this.currentPolylinePoints.size());
            }
        } else {
            Log.w(TAG, ">>> POINT SKIPPED by PolylineManager. AddGPS=" + shouldAddBasedOnGPS + ", NewSess=" + newSessionStarted + ", RecActive=" + this.isRecordingActiveForCurrentSegment + ", ActiveMove=" + isActiveMovementMode);
            if (!shouldAddBasedOnGPS)
                Log.d(TAG, "processNewLocation: Point skipped by GPS filter (time/distance).");
            else if (newSessionStarted)
                Log.d(TAG, "processNewLocation: Point skipped (new session just started).");
            else if (!this.isRecordingActiveForCurrentSegment)
                Log.d(TAG, "processNewLocation: Point skipped (recording not active for segment yet).");
            Log.d(TAG, "processNewLocation: POINT ADDED. Mode: " + effectiveMode + ", Acc: " + accuracy + ", RecActive: true, Pts: " + this.currentPolylinePoints.size());
            Log.d("AccuracyDebug", "PolylineManager processNewLocation: SKIPPED point, received accuracy was = " + accuracy);
        }
        // --- End Add Point ---

        // --- Update Last Processed Timestamp (regardless of adding point) ---
        this.lastLocationTimestamp = currentTime;

        // --- Return Final Mode determined for this specific location update ---
        return effectiveMode;
    }

    // Add this helper method if it was deleted
    private String activityTypeToString(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "IN_VEHICLE";
            case DetectedActivity.ON_BICYCLE:
                return "ON_BICYCLE";
            case DetectedActivity.ON_FOOT:
                return "ON_FOOT";
            case DetectedActivity.RUNNING:
                return "RUNNING";
            case DetectedActivity.STILL:
                return "STILL";
            case DetectedActivity.TILTING:
                return "TILTING";
            case DetectedActivity.UNKNOWN:
                return "UNKNOWN";
            case DetectedActivity.WALKING:
                return "WALKING";
            default:
                return "Unrecognized(" + activityType + ")";
        }
    }


    /**
     * Checks if points are actively being recorded for the current segment
     * (i.e., not waiting for initial distance threshold).
     *
     * @return true if recording is active, false otherwise.
     */
    public synchronized boolean isRecordingActive() {
        return this.isRecordingActiveForCurrentSegment;
    }


    // METHOD DEFINITION for initializeNewSegmentInternal (must accept 3 arguments)
    private synchronized void initializeNewSegmentInternal(String initialTransportMode, @Nullable Location startLoc, boolean bypassInitialWaitConditions) {
        Log.d(TAG, "Initializing new segment state. Mode: " + initialTransportMode + ", BypassWaits: " + bypassInitialWaitConditions);
        this.currentPolylinePoints = new ArrayList<>();
        this.currentSegmentDominantMode = initialTransportMode;
        this.lastPolylinePointTimeAdded = 0;
        this.segmentStartLocation = null;
        this.isWaitingForInitialDistance = false;
        this.isRecordingActiveForCurrentSegment = false;
        this.accuratePointsAfterInitialDistanceCount = 0;
        this.recentAccuracies.clear();
        this.lastLocationTimestamp = (startLoc != null) ? startLoc.getTime() : System.currentTimeMillis();

        if (bypassInitialWaitConditions) {
            Log.i(TAG, "Bypassing initial wait conditions for segment (" + initialTransportMode + "). Recording ACTIVE.");
            this.isRecordingActiveForCurrentSegment = true;
        } else if (startLoc != null && this.initialDistanceThresholdMeters > 0) {
            this.isWaitingForInitialDistance = true;
            this.segmentStartLocation = startLoc;
            Log.i(TAG, "Initializing segment (" + initialTransportMode + "). Waiting for " + this.initialDistanceThresholdMeters + "m distance. Recording NOT active.");
        } else {
            this.isRecordingActiveForCurrentSegment = true;
            Log.i(TAG, "Initializing segment (" + initialTransportMode + "). Distance threshold inactive or no start location. Recording ACTIVE.");
        }
    }


    /**
     * Starts a new polyline segment. Checks if the previous segment should be saved
     * before resetting the state for the new segment.
     *
     * @param newTransportMode The transport mode for the new segment being started.
     */
    private void startNewSegment(String newTransportMode, @Nullable Location startLoc) { // Added @Nullable Location startLoc
        Log.i(TAG, "Starting new segment. New Mode: " + newTransportMode);

        // 1. Check and save previous segment (existing logic remains the same)
        if (this.currentPolylinePoints != null && !this.currentPolylinePoints.isEmpty()) {
            // ... (existing saving logic) ...
            savePolylineDataWithTimestamp(); // Trigger save check for the segment ending now
        } else {
            Log.d(TAG, "Previous segment was empty, nothing to save.");
        }

        // 2. Initialize the state for the NEW segment, passing the start location
        // <<< --- MODIFICATION --- >>>
        initializeNewSegmentInternal(newTransportMode, startLoc, false); // Pass false
    }




    private boolean shouldAddPoint(Location currentLocation, long currentTime, long minTimeInterval, float currentMinDistanceThreshold) {
        // Check against the manager's state variable for the last added point
        PolylinePoint lastAddedPoint = null;
        if (this.currentPolylinePoints != null && !this.currentPolylinePoints.isEmpty()) {
            lastAddedPoint = this.currentPolylinePoints.get(this.currentPolylinePoints.size() - 1);
        }

        // If no points have been added yet, always add the first one
        if (lastAddedPoint == null) {
            return true;
        }

        // Calculate time delta using the manager's timestamp
        long timeDelta = currentTime - this.lastPolylinePointTimeAdded;

        // Create a temporary Location object from the last added GeoPoint for distance calculation
        Location lastPointLocation = new Location("");
        lastPointLocation.setLatitude(lastAddedPoint.latitude);
        lastPointLocation.setLongitude(lastAddedPoint.longitude);

        float distance = currentLocation.distanceTo(lastPointLocation);

        boolean timePassed = timeDelta >= minTimeInterval;
        boolean movedEnough = distance >= currentMinDistanceThreshold;
        boolean addPoint = timePassed || movedEnough;


        // Optional logging
        // if (!addPoint) Log.d(TAG, "Filter Skip: TimeDelta=" + timeDelta + ", Dist=" + String.format("%.1f", distance) + "m (Threshold=" + currentMinDistanceThreshold + "m)");

        return addPoint;
    }

    // Significance check using TransportSpeed constants


    private boolean isPolylineInsignificant(List<PolylinePoint> points) {
        if (points == null || points.size() < 2) return true;
        // Determine the primary mode of the segment (e.g., the first point's mode)
        String transportMode = points.get(0).transportMode != null ? points.get(0).transportMode : "Unknown";

        int minPoints;
        long minDuration;
        float minDistance;

        try {
            switch (transportMode) {
                case "Walking":
                    minPoints = TransportSpeed.WALKING_MIN_POINTS;
                    minDuration = TransportSpeed.WALKING_MIN_DURATION;
                    minDistance = TransportSpeed.WALKING_MIN_DISTANCE;
                    break;
                case "Bicycling":
                    minPoints = TransportSpeed.BICYCLING_MIN_POINTS;
                    minDuration = TransportSpeed.BICYCLING_MIN_DURATION;
                    minDistance = TransportSpeed.BICYCLING_MIN_DISTANCE;
                    break;
                case "In Vehicle":
                    minPoints = TransportSpeed.IN_VEHICLE_MIN_POINTS;
                    minDuration = TransportSpeed.IN_VEHICLE_MIN_DURATION;
                    minDistance = TransportSpeed.IN_VEHICLE_MIN_DISTANCE;
                    break;
                // *** END ADDED CASE ***
                default: // Handle "Unknown" or other unexpected modes
                    // Use relatively strict defaults if mode is unknown
                    minPoints = 10;
                    minDuration = 30000;
                    minDistance = 20.0f;
                    Log.w(TAG, "isPolylineInsignificant: Unknown transportMode '" + transportMode + "', using default thresholds.");
                    break;
            }
        } catch (NoClassDefFoundError | Exception e) {
            Log.e(TAG, "Constants class missing/error for significance check (Mode: " + transportMode + ").", e);
            // Fallback defaults if constants fail
            minPoints = 10;
            minDuration = 30000;
            minDistance = 20.0f;
        }

        // Check points count
        if (points.size() < minPoints) {
            Log.d(TAG, "Segment INSIGNIFICANT (Mode=" + transportMode + "): Points=" + points.size() + " < MinPoints=" + minPoints);
            return true;
        }
        // Check duration
        long duration = points.get(points.size() - 1).timestamp - points.get(0).timestamp;
        if (duration < minDuration) {
            Log.d(TAG, "Segment INSIGNIFICANT (Mode=" + transportMode + "): Duration=" + duration + "ms < MinDuration=" + minDuration + "ms");
            return true;
        }
        // Check distance
        float totalDistance = 0;
        Location lastLoc = null;
        for (PolylinePoint p : points) {
            Location currentLoc = new Location("");
            currentLoc.setLatitude(p.latitude);
            currentLoc.setLongitude(p.longitude);
            if (lastLoc != null) totalDistance += currentLoc.distanceTo(lastLoc);
            lastLoc = currentLoc;
        }
        if (totalDistance < minDistance) {
            Log.d(TAG, "Segment INSIGNIFICANT (Mode=" + transportMode + "): Distance=" + String.format("%.1f", totalDistance) + "m < MinDistance=" + String.format("%.1f", minDistance) + "m");
            return true;
        }

        Log.d(TAG, "Segment SIGNIFICANT: Mode=" + transportMode + ", Pts=" + points.size() + ", Dur=" + duration + "ms, Dist=" + String.format("%.1f", totalDistance) + "m");
        return false; // It's significant
    }

    /**
     * Public trigger for saving the current polylinePoints list.
     * Checks cooldown and significance before queueing the save on the background executor.
     * Uses the PolylineManager's state and methods.
     */
    public void savePolylineDataWithTimestamp() {
        long currentTime = System.currentTimeMillis();

        // *** MODIFICATION START ***
        // 1. Check if a save is already pending/in progress
        if (isSavePending.get()) {
            Log.d(TAG, "Manager Save skipped: Save operation already pending.");
            return;
        }
        // 2. Attempt to atomically set the flag to true
        if (!isSavePending.compareAndSet(false, true)) {
            // If compareAndSet returns false, another thread just set it.
            Log.d(TAG, "Manager Save skipped: Another thread just initiated save.");
            return;
        }
        // *** If we reach here, isSavePending is now true ***
        // *** MODIFICATION END ***

        // 3. Check cooldown (optional redundancy, but keep for now)
        if (currentTime - this.lastSaveTime < SAVE_COOLDOWN) {
            Log.d(TAG, "Manager Save skipped due to cooldown. Resetting pending flag.");
            isSavePending.set(false); // <<< Reset flag if skipping due to cooldown
            return;
        }

        // 4. Make a copy for the background thread
        if (this.currentPolylinePoints == null || this.currentPolylinePoints.isEmpty()) {
            Log.d(TAG, "Manager Save skipped: polylinePoints list is empty. Resetting pending flag.");
            isSavePending.set(false); // <<< Reset flag if skipping due to empty list
            return;
        }
        List<PolylinePoint> pointsToSave = new ArrayList<>(this.currentPolylinePoints);
        // Store the time *when the save was triggered*
        final long triggerTime = currentTime;

        // 5. Execute background save task
        this.backgroundExecutor.execute(() -> {
            try { // *** ADD try block ***
                // Use the manager's isPolylineInsignificant method
                if (!isPolylineInsignificant(pointsToSave)) {
                    // Use the manager's savePolylineDataInternal method
                    // Pass the time the save was *triggered*
                    savePolylineDataInternal(pointsToSave, triggerTime);
                    // Update lastSaveTime *after* successful internal save logic completes
                    // Consider using AtomicLong if accessed from multiple threads,
                    // but setting it here after background work might be sufficient
                    // if the primary check is the AtomicBoolean.
                    this.lastSaveTime = triggerTime; // Update here instead of posting back
                    Log.d(TAG, "Updated lastSaveTime after background save: " + triggerTime);
                } else {
                    Log.d(TAG, "Manager Save skipped (BG check): Insignificant.");
                }
            } catch (Exception e) { // Catch potential errors in background task
                Log.e(TAG, "Error during background save execution", e);
            } finally { // *** ADD finally block ***
                isSavePending.set(false); // <<< ALWAYS reset the flag when task finishes
                Log.d(TAG, "Background save task finished. Reset isSavePending flag.");
            }
        });

        // Log that the save task was queued (isSavePending is still true here)
        Log.d(TAG, "Save task queued successfully. isSavePending=true");
    }

    /**
     * Forces the end of the current segment (saving if significant) and starts
     * collecting data for a new segment with the specified mode.
     * Intended for manual triggers like mode overrides.
     *
     * @param newTransportMode The transport mode for the new segment.
     * @param startLoc The location where the new segment is considered to start.
     */
    public synchronized void forceNewSegment(String newTransportMode, @Nullable Location startLoc, boolean bypassInitialWaitConditions) {
        Log.i(TAG, "Forcing new segment due to external trigger. New Mode: " + newTransportMode + ", BypassWaits: " + bypassInitialWaitConditions);
        savePolylineDataWithTimestamp();
        // This call now uses the 'bypassInitialWaitConditions' parameter it received
        initializeNewSegmentInternal(newTransportMode, startLoc, bypassInitialWaitConditions);
    }

    /**
     * Internal method to save a list of points to a JSON file.
     * Should be called from a background thread via the backgroundExecutor.
     * Uses the PolylineManager's dependencies (gson, context).
     *
     * @param pointsToSave The list of PolylinePoint objects to save.
     * @param saveTime     The timestamp to use for the filename and updating lastSaveTime.
     */
    private void savePolylineDataInternal(List<PolylinePoint> pointsToSave, long saveTime /*, String typeFlag */) {


        if (pointsToSave == null || pointsToSave.isEmpty()) {
            Log.w(TAG, "Internal Save: null or empty points list.");
            return;
        }
        // Use the manager's Gson instance
        if (this.gson == null) {
            Log.e(TAG, "Internal Save: Gson is null, cannot save.");
            return;
        }
        // Use the manager's application context to get the correct directory
        if (this.appContext == null) {
            Log.e(TAG, "Internal Save: AppContext is null, cannot get directory.");
            return;
        }

        String json = this.gson.toJson(pointsToSave);
        String timestamp = String.valueOf(saveTime);
        File directory = this.appContext.getExternalFilesDir(null); // Get directory using context

        if (directory == null) {
            Log.e(TAG, "Internal Save: Failed to get external files directory.");
            return;
        }

        File file = new File(directory, "polyline_data_" + timestamp + ".json");

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
            Log.i(TAG, "Polyline saved by Manager (BG): " + file.getName() + " (" + pointsToSave.size() + " pts)");
            // Post update to lastSaveTime via the Handler
        } catch (IOException e) { /* ... */ } catch (Exception e) { /* ... */ }
    }

    // --- Dynamic interval/timeout helpers ---
    private long getMinTimeIntervalForMode(String transportMode) {
        switch (transportMode) {
            case "Walking":
                return 2000; // 2 sec
            case "Bicycling":
                return 4000; // 4 sec
            case "In Vehicle":
                return 5000; // 5 sec
            default:
                return MIN_TIME_INTERVAL; // 3 sec
        }
    }

    private long getSessionTimeoutForMode(String transportMode) {
        switch (transportMode) {
            case "In Vehicle":
                return IN_VEHICLE_SESSION_TIMEOUT; // 45 sec
            default:
                return DEFAULT_SESSION_TIMEOUT; // 15 sec
        }
    }

    public synchronized void finalizeAndSaveCurrentSegment() {
        Log.d(TAG, "Finalizing last segment...");

        // --- Step 1: Check if there are points to save ---
        if (currentPolylinePoints == null || currentPolylinePoints.isEmpty()) {
            Log.d(TAG, "Finalize skipped: No points in current segment.");
            return;
        }

        // Make a copy for background processing
        final List<PolylinePoint> pointsToProcess = new ArrayList<>(currentPolylinePoints);


        // --- Step 2: Check Significance ---
        if (isPolylineInsignificant(pointsToProcess)) {
            Log.d(TAG, "Final segment insignificant, not saving.");
            // Still clear the state even if not saving
            initializeNewSegmentInternal("Unknown", null, false);
            return;
        }

        // --- Step 3: (Optional) Check Pending/Cooldown ---
         if (isSavePending.get()) {
             Log.d(TAG, "Finalize skipped: Save already pending.");
             // Don't clear state here if another save is pending
             return;
         }
         long currentTime = System.currentTimeMillis();
         if (currentTime - this.lastSaveTime < SAVE_COOLDOWN) {
             Log.d(TAG, "Finalize skipped: Within cooldown period.");
              // Don't clear state here if within cooldown
             return;
         }
         // Attempt to set pending flag if using this check
         if (!isSavePending.compareAndSet(false, true)) {
             Log.d(TAG, "Finalize skipped: Another thread just initiated save.");
             return;
         }

        Log.d(TAG, "Final segment is significant. Queuing final save task.");
        final long finalSaveTime = System.currentTimeMillis(); // Use current time for filename


        // --- Step 4: Queue Save Task ---
        backgroundExecutor.execute(() -> {
            try {
                // Accuracy check logic remains the same...
                boolean needsMapMatching = checkAccuracyRequiresMatching(pointsToProcess);
                Log.d(TAG, "finalizeAndSave (BG): checkAccuracyRequiresMatching returned: " + needsMapMatching);

                if (needsMapMatching) {
                    // ... (Map Matching logic as before) ...
                    // Placeholder: just save raw for now
                    savePolylineDataInternal(pointsToProcess, finalSaveTime);
                } else {
                    Log.i(TAG, "finalizeAndSave (BG): Skipping map matching path due to high accuracy.");
                    savePolylineDataInternal(pointsToProcess, finalSaveTime);
                }
                // lastSaveTime = finalSaveTime; // Update last save time if needed (optional if pending flag used)

            } catch (Exception e) {
                Log.e(TAG, "Error during final background save execution", e);
            } finally {
                // Reset pending flag if you added the check above
                // isSavePending.set(false);
            }
        });

        // --- Step 5: Clear State IMMEDIATELY after queuing ---
        Log.d(TAG, "Clearing current segment state after queuing final save.");
        initializeNewSegmentInternal("Unknown", null, false); // Add false for bypass
    }

    /**
     * Helper method to determine if map matching is needed based on accuracy.
     * Returns true if matching is recommended, false otherwise.
     */
    // Inside PolylineManager.java
    private boolean checkAccuracyRequiresMatching(List<PolylinePoint> points) {
        if (points == null || points.isEmpty()) {
            Log.d(TAG_MATCH_CHECK, "checkAccuracyRequiresMatching: Points list null or empty. Returning false (no match).");
            return false; // Nothing to match
        }

        Log.i(TAG_MATCH_CHECK, "--- Checking Accuracy for Matching (" + points.size() + " points) ---");
        int highAccuracyCount = 0;
        int validAccuracyPoints = 0; // Count points that *have* a valid accuracy
        for (PolylinePoint point : points) {
            // Log accuracy of each point if needed (can be verbose)
            // Log.v(TAG_MATCH_CHECK, "  Point accuracy: " + point.accuracy);

            if (point.accuracy > 0) { // Check if accuracy is valid
                validAccuracyPoints++;
                if (point.accuracy <= SKIP_MATCHING_ACCURACY_THRESHOLD) { // Check against threshold
                    highAccuracyCount++;
                }
            } else {
                // Log invalid accuracy points if you want to see them
                Log.w(TAG_MATCH_CHECK, "  Point has invalid accuracy: " + point.accuracy);
                // Option: Immediately require matching if *any* point is invalid?
                // Log.w(TAG_MATCH_CHECK, "  --> Invalid accuracy found, forcing matching.");
                // return true;
            }
        }

        if (validAccuracyPoints == 0) {
            Log.w(TAG_MATCH_CHECK, "No valid accuracy points found. Returning true (NEEDS MATCHING).");
            return true; // No valid accuracy data, assume matching is needed
        }

        // --- Your chosen logic (e.g., 80% threshold) ---
        double highAccuracyRatio = (double) highAccuracyCount / validAccuracyPoints;
        boolean mostlyHighAccuracy = highAccuracyRatio >= 0.80;
        // --- End chosen logic ---

        Log.d(TAG, "checkAccuracyRequiresMatching: ValidPts=" + validAccuracyPoints + ", HighAccPts=" + highAccuracyCount + ", Ratio=" + String.format("%.2f", highAccuracyRatio) + ", MostlyHighAcc=" + mostlyHighAccuracy);
        boolean needsMatching = !mostlyHighAccuracy; // Needs matching if NOT mostly high accuracy
        Log.d(TAG, "checkAccuracyRequiresMatching: Returning needsMatching=" + needsMatching);

        Log.i(TAG_MATCH_CHECK, "Valid Points: " + validAccuracyPoints);
        Log.i(TAG_MATCH_CHECK, "High Accuracy Points (<= " + SKIP_MATCHING_ACCURACY_THRESHOLD + "m): " + highAccuracyCount);
        Log.i(TAG_MATCH_CHECK, String.format(Locale.US, "High Accuracy Ratio: %.2f", highAccuracyRatio));
        Log.i(TAG_MATCH_CHECK, "Needs Matching (< 0.80): " + needsMatching);
        Log.i(TAG_MATCH_CHECK, "------------------------------------------");

        return needsMatching;
    }

    /**
     * Returns a copy of the list of points for the currently active segment.
     * Returns an empty list if no points have been collected for the current segment.
     * @return A new list containing the current polyline points.
     */
    public synchronized List<PolylinePoint> getCurrentSegmentPoints() {
        if (this.currentPolylinePoints != null) {
            // Return a copy to prevent modification issues outside the manager
            return new ArrayList<>(this.currentPolylinePoints);
        } else {
            // Return an empty list if the internal list is null
            return new ArrayList<>();
        }
    }

    /**
     * Gets the last point successfully added to the current segment's data list.
     * Used by the service to broadcast the newly added point details.
     * @return A LatLng object representing the last added point, or null if the list is empty.
     */
    @Nullable // Indicates it might return null
    public LatLng getLastAddedPoint() { // MODIFIED Return Type
        LatLng pointToSend = null;
        synchronized (this) { // Ensure thread safety when accessing list
            if (currentPolylinePoints != null && !currentPolylinePoints.isEmpty()) {
                PolylinePoint lastPoint = currentPolylinePoints.get(currentPolylinePoints.size() - 1);
                pointToSend = new LatLng(lastPoint.latitude, lastPoint.longitude); // Create LatLng
            }
        }
        Log.d(TAG, "getLastAddedPoint() returning: " + (pointToSend != null ? pointToSend.toString() : "null"));
        return pointToSend;
    }

    private synchronized void addAccuracyToWindow(float accuracy) {
        if (accuracy <= 0) return; // Don't add invalid accuracies

        if (recentAccuracies.size() >= ACCURACY_WINDOW_SIZE) {
            recentAccuracies.remove(0); // Remove oldest if window is full
        }
        recentAccuracies.add(accuracy);
    }
}

