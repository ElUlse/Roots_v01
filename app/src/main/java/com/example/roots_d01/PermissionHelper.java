package com.example.roots_d01;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast; // Import Toast

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

// Class to handle permission requests and results
public class PermissionHelper {

    private static final String TAG = "PermissionHelper";

    // --- Constants Moved from MainActivity ---
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    public static final int ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE = 3; // Keep AR code if handling here

    private final Activity activity; // Need activity context for requests
    private final UiUpdater uiUpdater; // Need updater for UI feedback on denial
    private final PermissionResultListener listener; // Callback listener

    // --- Listener Interface ---
    public interface PermissionResultListener {
        void onLocationPermissionGranted();
        void onLocationPermissionDenied();
        void onActivityRecognitionPermissionGranted();
        void onActivityRecognitionPermissionDenied();
        // Add other permission results if needed
    }

    // Constructor
    public PermissionHelper(@NonNull Activity activity, @NonNull UiUpdater uiUpdater, @NonNull PermissionResultListener listener) {
        this.activity = activity;
        this.uiUpdater = uiUpdater;
        this.listener = listener;
    }

    // --- Method: checkAndRequestPermissions (Moved from MainActivity) ---
    public void checkAndRequestBasePermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // Location Permission
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + permissionsToRequest);
            ActivityCompat.requestPermissions(activity, permissionsToRequest.toArray(new String[0]), LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "Base permissions (Location/Notification) already granted.");
            // Notify listener that location permission is granted (since it's the critical one checked here)
            listener.onLocationPermissionGranted();
            // Note: Activity Recognition permission is requested separately after location is granted.
        }
    }
    // --- End Method: checkAndRequestBasePermissions ---

    // --- Method: handlePermissionsResult (Handles logic from MainActivity.onRequestPermissionsResult) ---
    public void handlePermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "handlePermissionsResult: RequestCode=" + requestCode);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean locationGranted = false;
            // Check fine location result
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    locationGranted = true;
                    break;
                }
            }

            if (locationGranted) {
                Log.d(TAG, "Location permission GRANTED.");
                listener.onLocationPermissionGranted(); // Notify listener
            } else {
                Log.w(TAG, "Location permission DENIED.");
                if (uiUpdater != null) {
                    uiUpdater.updateGPSIndicator(0); // Update UI via helper
                }
                listener.onLocationPermissionDenied(); // Notify listener
            }

            // Check notification result (informative, doesn't block core functionality)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(Manifest.permission.POST_NOTIFICATIONS) && grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "Post Notifications permission DENIED.");
                        // Show toast directly from helper is okay for simple feedback
                        Toast.makeText(activity, "Notifications disabled.", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }
        }
        // --- Handle Activity Recognition Result ---
        // Note: We might move AR request logic here too later
        else if (requestCode == ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Activity Recognition permission GRANTED.");
                listener.onActivityRecognitionPermissionGranted(); // Notify listener
            } else {
                Log.w(TAG, "Activity Recognition permission DENIED.");
                listener.onActivityRecognitionPermissionDenied(); // Notify listener
            }
        }
        // --- End Handle Activity Recognition ---
    }
    // --- End Method: handlePermissionsResult ---

} // End of PermissionHelper class