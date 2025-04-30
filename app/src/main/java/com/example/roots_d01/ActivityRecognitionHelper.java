package com.example.roots_d01;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.tasks.Task;

public class ActivityRecognitionHelper {

    private static final String TAG = "ActivityRecHelper";
    private static final long ACTIVITY_DETECTION_INTERVAL = 5000; // 5 seconds

    private final Context context;
    private final ActivityRecognitionClient activityRecognitionClient;

    public ActivityRecognitionHelper(Context context) {
        this.context = context.getApplicationContext();
        this.activityRecognitionClient = ActivityRecognition.getClient(this.context);
        Log.d(TAG, "ActivityRecognitionHelper initialized.");
    }

    // --- Method: getActivityDetectionPendingIntent (Moved from MainActivity, now private) ---
    private PendingIntent getActivityDetectionPendingIntent() {
        // Ensure DetectedActivitiesBroadcastReceiver exists and is registered correctly
        Intent intent = new Intent(context, DetectedActivitiesBroadcastReceiver.class);
        // Using FLAG_IMMUTABLE as required for newer Android versions
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    // --- End Method: getActivityDetectionPendingIntent ---


    // --- Method: requestActivityUpdatesInternal (Moved from MainActivity) ---
    /**
     * Internal method to request activity updates. Requires permission check beforehand.
     * @return Task<Void> The task associated with the request. Null if client is null.
     */
    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    public Task<Void> requestActivityUpdatesInternal() {
        Log.d(TAG, "Requesting activity updates internally.");
        if (activityRecognitionClient == null) {
            Log.e(TAG, "ActivityRecognitionClient is null, cannot request updates.");
            return null;
        }
        try {
            PendingIntent pendingIntent = getActivityDetectionPendingIntent();
            Task<Void> task = activityRecognitionClient.requestActivityUpdates(
                    ACTIVITY_DETECTION_INTERVAL, pendingIntent);

            task.addOnSuccessListener(aVoid -> Log.d(TAG, "Activity updates request success (internal)."));
            task.addOnFailureListener(e -> Log.e(TAG, "Activity updates request failure (internal).", e));
            return task; // Return the task
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException requesting activity updates internal. Check permission.", e);
            return null; // Indicate failure due to permission
        } catch (Exception e) {
            Log.e(TAG, "Exception requesting activity updates internal.", e);
            return null; // Indicate general failure
        }
    }
    // --- End Method: requestActivityUpdatesInternal ---


    // --- Method: removeActivityUpdates (Moved from MainActivity) ---
    /**
     * Removes activity updates associated with the helper's PendingIntent.
     * Requires ACTIVITY_RECOGNITION permission (implicitly by client usage).
     * @return Task<Void> The task associated with the removal request. Null if client is null.
     */
    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION) // Or inferred permission
    public Task<Void> removeActivityUpdates() {
        Log.d(TAG, "Removing activity updates.");
        if (activityRecognitionClient != null) {
            try {
                PendingIntent pendingIntent = getActivityDetectionPendingIntent();
                Task<Void> task = activityRecognitionClient.removeActivityUpdates(pendingIntent);
                task.addOnSuccessListener(aVoid -> Log.d(TAG, "Activity updates removed successfully."))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to remove activity updates.", e));
                return task; // Return the task
            } catch (Exception e) {
                Log.e(TAG, "Exception removing activity updates.", e);
                return null; // Indicate failure
            }
        } else {
            Log.e(TAG, "ActivityRecognitionClient is null, cannot remove updates.");
            return null;
        }
    }
    // --- End Method: removeActivityUpdates ---

} // End of ActivityRecognitionHelper class