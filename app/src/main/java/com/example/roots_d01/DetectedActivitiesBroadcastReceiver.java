package com.example.roots_d01;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;
import java.util.ArrayList;

public class DetectedActivitiesBroadcastReceiver extends BroadcastReceiver {

    // Use specific tags for clarity
    private static final String RECEIVER_TAG = "AR_Receiver_Debug"; // New tag for detailed logs
    private static final String FORWARD_TAG = "AutoTrack_ARReceiver"; // Tag for forwarding log


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(RECEIVER_TAG, "Broadcast Received by DetectedActivitiesBroadcastReceiver");

        if (intent != null && ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            if (result != null) {
                // --- Step 1: Default to most probable initially ---
                DetectedActivity mostProbableActivity = result.getMostProbableActivity();
                // Use these variables to store the final type/confidence to send
                int finalActivityType = mostProbableActivity.getType();
                int finalConfidence = mostProbableActivity.getConfidence();
                String logReason = "Most Probable"; // For logging the decision reason

                // --- Log Most Probable ---
                Log.i(RECEIVER_TAG, "Most Probable Activity: " + getActivityName(finalActivityType) + " (Conf: " + finalConfidence + ")");

                // --- Step 2: Get all probable activities ---
                Log.d("AR_AllProbable", "--- All Probable Activities ---");
                List<DetectedActivity> allActivities = result.getProbableActivities();

                // --- Step 3: Check all activities to potentially override with priority ---
                if (allActivities != null && !allActivities.isEmpty()) {
                    boolean specificFound = false;
                    int specificType = -1; // Track the type of specific activity found (Running=8, Walking=7)

                    for (DetectedActivity activity : allActivities) {
                        Log.d("AR_AllProbable", "  -> Activity: " + getActivityName(activity.getType()) + " (Conf: " + activity.getConfidence() + ")");
                        int currentType = activity.getType();
                        int currentConfidence = activity.getConfidence();

                        // Prioritize RUNNING first if confidence is high

                        // Else, check for WALKING if confidence is high AND we haven't already locked in Running
                        if (currentType == DetectedActivity.WALKING && currentConfidence >= 75) {
                            // Take Walking only if Running wasn't already found
                            finalActivityType = currentType;
                            finalConfidence = currentConfidence;
                            logReason = "Prioritized Walking";
                            specificFound = true;
                            specificType = currentType; // Track that we found Walking
                            // Continue loop in case Running is found later with high confidence
                        }
                        // Add similar 'else if' block here if you implement "In Vehicle" priority later
                    }

                    // --- Step 4: Fallback Check (if no specific Running/Walking was prioritized) ---
                    if (!specificFound) {
                        // The finalActivityType is still the original 'mostProbableActivity'
                        // Optionally: Add extra check here if needed, e.g., ensure ON_FOOT confidence is adequate
                        if (finalActivityType == DetectedActivity.ON_FOOT && finalConfidence < 65) {
                            // Example: If most probable is ON_FOOT but confidence is too low, maybe send UNKNOWN?
                            // finalActivityType = DetectedActivity.UNKNOWN;
                            // finalConfidence = 0; // Confidence for Unknown is irrelevant
                            // logReason = "Fallback Unknown (Low Conf ON_FOOT)";
                        } else {
                            // Keep the original most probable if it passed any necessary checks
                            logReason = "Kept Most Probable (No Specific Priority Met)";
                        }
                    }
                } else {
                    Log.d("AR_AllProbable", "  (No probable activities list returned or empty)");
                    // Keep the original most probable if list is empty
                    logReason = "Kept Most Probable (Empty List)";
                }
                Log.d("AR_AllProbable", "-------------------------------");


                // --- Step 5: Send Broadcast to Service ---
                Intent activityIntent = new Intent(LocationTrackingService.ACTION_ACTIVITY_UPDATE);
                activityIntent.putExtra(LocationTrackingService.EXTRA_DETECTED_ACTIVITY_TYPE, finalActivityType); // Send the final chosen type
                activityIntent.putExtra(LocationTrackingService.EXTRA_DETECTED_ACTIVITY_CONFIDENCE, finalConfidence); // Send the final chosen confidence
                Log.d(FORWARD_TAG, "Sending ACTION_ACTIVITY_UPDATE (" + logReason + "). Type=" + finalActivityType + " (" + getActivityName(finalActivityType) + "), Conf=" + finalConfidence);
                LocalBroadcastManager.getInstance(context).sendBroadcast(activityIntent);


                List<DetectedActivity> allActivitiesForUI = result.getProbableActivities();
                if (allActivitiesForUI != null && !allActivitiesForUI.isEmpty()) {
                    Intent mainActivityIntent = new Intent(MainActivity.ACTION_ALL_ACTIVITIES_UPDATE);
                    // DetectedActivity is Parcelable, so we can put the ArrayList
                    mainActivityIntent.putParcelableArrayListExtra(MainActivity.EXTRA_ALL_ACTIVITIES, new ArrayList<>(allActivitiesForUI));
                    Log.d(FORWARD_TAG, "Sending ACTION_ALL_ACTIVITIES_UPDATE to MainActivity with " + allActivitiesForUI.size() + " activities.");
                    LocalBroadcastManager.getInstance(context).sendBroadcast(mainActivityIntent);
                } else {
                    Log.d(FORWARD_TAG, "Not sending full list to MainActivity - list is null or empty.");
                }

            } else {
                Log.w(FORWARD_TAG, "ActivityRecognitionResult was null even though hasResult was true.");
            }
        } else if (intent != null) {
            Log.w(FORWARD_TAG, "Intent did not contain ActivityRecognitionResult (hasResult=false). Action: " + intent.getAction());
        } else {
            Log.w(FORWARD_TAG, "Intent received was null.");
        }
    }

    // Helper method getActivityName remains the same
    private String getActivityName(int activityType) {
        // ... (keep existing implementation) ...
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
}