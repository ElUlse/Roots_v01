package com.example.roots_d01;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import androidx.core.content.ContextCompat;

// Class to manage UI element updates
public class UiUpdater {

    private static final String TAG = "UiUpdater";
    // private static final String STATUS_CHECKING_ACTIVITY = "Checking Activity..."; // Add later if needed

    private final Context context;
    // References to UI elements passed from MainActivity
    private final Button gpsStatusButton;
    private final ImageView transportModeIcon;
    private final FloatingActionButton startStopFab;
    private final SwitchMaterial trackingModeSwitch;

    private Animation pulseAnimation = null;

    private static final String MODE_AUTO = "auto";
    private static final String MODE_MANUAL = "manual";
    private static final String STATUS_AWAITING_MOVEMENT = "Awaiting Movement"; // Example text
    private static final String STATUS_MANUAL_MODE = "Manual Mode";


    // Constructor accepting all required UI elements
    public UiUpdater(Context context, Button gpsStatusButton, ImageView transportModeIcon,
           FloatingActionButton startStopFab,
                     SwitchMaterial trackingModeSwitch) {
        this.context = context.getApplicationContext(); // Use application context
        this.gpsStatusButton = gpsStatusButton;
        this.transportModeIcon = transportModeIcon;
        this.startStopFab = startStopFab;
        this.trackingModeSwitch = trackingModeSwitch;
    }

    // --- Method: updateGPSIndicator (Moved from MainActivity) ---

    /**
     * Updates the background tint of the GPS status button.
     *
     * @param status 0=No GPS, 1=Good GPS, 2=Poor GPS, other=Gray
     */
    public void updateGPSIndicator(int status) {
        if (gpsStatusButton == null) {
            Log.w(TAG, "updateGPSIndicator: gpsStatusButton is null.");
            return; // Avoid crash if button wasn't passed correctly
        }
        int color;
        switch (status) {
            case 0:
                color = Color.RED;
                break;       // No GPS
            case 1:
                color = Color.GREEN;
                break;     // GPS Locked (Good)
            case 2:
                color = Color.YELLOW;
                break;    // Poor GPS
            default:
                color = Color.GRAY;
                break;     // Unknown state
        }
        // Set tint using the member variable
        gpsStatusButton.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    /**
     * Determines GPS status based on a Location object and updates the indicator.
     *
     * @param location The location object, can be null.
     */
    public void updateGPSIndicatorFromLocation(@Nullable Location location) {
        int status = 0; // Default to No GPS
        if (location == null) {
            status = 0; // No location = No GPS
        } else if (location.hasAccuracy()) {
            // Determine status based on accuracy: > 25m is poor (2), otherwise good (1)
            status = location.getAccuracy() > 25 ? 2 : 1;
        } else {
            status = 0; // Has location but no accuracy info - treat as No GPS / Unknown
        }
        // Call the other method within this class
        this.updateGPSIndicator(status);
    }


    // --- Method: updateStartStopButtonState (Moved from MainActivity) ---

    /**
     * Updates the visual state of the Start/Stop FAB and the transport mode icon's background/animation.
     *
     * @param isTrackingActive The current tracking state from MainActivity.
     */
    public void updateStartStopButtonState(boolean isTrackingActive, @Nullable String effectiveMode) {
        // --- Update FAB State ---
        if (this.startStopFab == null) { // Use member variable
            Log.w(TAG, "updateStartStopButtonState: startStopFab is null!");
        } else {
            if (isTrackingActive) {
                this.startStopFab.setImageResource(R.drawable.ic_stop);
                this.startStopFab.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
                Log.d(TAG, "updateStartStopButtonState: Set FAB to STOP");
            } else {
                this.startStopFab.setImageResource(R.drawable.ic_play);
                // Get primary color using member context
                // Explicitly set the background color for the inactive (play) state
                int explicitPlayColor = ContextCompat.getColor(context, R.color.md_theme_dark_errorContainer); // Use purple_500 or another opaque color like R.color.black
                this.startStopFab.setBackgroundTintList(ColorStateList.valueOf(explicitPlayColor));
                Log.d(TAG, "updateStartStopButtonState: Set FAB to PLAY");
            }
        }

        boolean showIconAsActive = isTrackingActive &&
                effectiveMode != null &&
                !effectiveMode.equals("Still") &&
                !effectiveMode.equals("Unknown");

        // --- Update Transport Icon Background (Outline) & Animation ---
        if (this.transportModeIcon != null) {
            // Use the calculated showIconAsActive flag
            if (showIconAsActive) {
                this.transportModeIcon.setVisibility(View.VISIBLE);
                Log.d(TAG, "updateStartStopButtonState: Set transport icon background to ACTIVE (with stroke)");

                // Start pulsing animation (existing logic)
                if (this.transportModeIcon.getAnimation() == null) {
                    if (this.pulseAnimation == null) {
                        this.pulseAnimation = new AlphaAnimation(1.0f, 0.4f);
                        this.pulseAnimation.setDuration(700);
                        this.pulseAnimation.setRepeatMode(Animation.REVERSE);
                        this.pulseAnimation.setRepeatCount(Animation.INFINITE);
                    }
                    this.transportModeIcon.startAnimation(this.pulseAnimation);
                    Log.d(TAG, "updateStartStopButtonState: Started pulsing animation");
                }
            } else { // Tracking is inactive OR mode is Still/Unknown
                this.transportModeIcon.setVisibility(View.GONE);
                Log.d(TAG, "updateStartStopButtonState: Set transport icon background to INACTIVE (no stroke)");

                // Stop pulsing animation (existing logic)
                if (this.transportModeIcon.getAnimation() != null) {
                    this.transportModeIcon.clearAnimation();
                    this.transportModeIcon.setAlpha(1.0f); // Reset alpha
                    Log.d(TAG, "updateStartStopButtonState: Cleared pulsing animation");
                }
            }
        } else {
            Log.w(TAG, "updateStartStopButtonState: transportModeIcon is null!");
        }
    }

    // --- Method: updateTransportModeIcon (Moved from MainActivity) ---

    /**
     * Sets the appropriate drawable resource for the transport mode icon based on the mode string.
     * Hides the icon for null or "Unknown" modes.
     *
     * @param transportMode The string representing the transport mode (e.g., "Walking", "Bicycling", "Determining").
     */
    public void updateTransportModeIcon(@Nullable String transportMode) {
        if (this.transportModeIcon == null) { // Use member variable
            Log.w(TAG, "updateTransportModeIcon: transportModeIcon member variable is null.");
            return;
        }

        // Use String comparison for "Determining" to avoid direct service dependency
        String modeToUse = (transportMode == null) ? "Unknown" : transportMode;
        Log.d(TAG, "Updating transport icon for mode: " + modeToUse); // Log entry

        int drawableId = 0; // Use 0 to indicate clearing the icon

        try {
            switch (modeToUse) {
                case "Walking":
                    drawableId = R.drawable.ic_walking;
                    break;
                case "Bicycling":
                    drawableId = R.drawable.ic_directions_bike;
                    break;
                case "In Vehicle":
                    drawableId = R.drawable.ic_directions_in_vehicle;
                    break;
                case "Unknown":
                default:
                    // drawableId remains 0, icon will be hidden
                    break;
            }

            // Set the image resource
            this.transportModeIcon.setImageResource(drawableId);
            Log.d(TAG, "Set transport icon resource ID: " + drawableId + " for mode: " + modeToUse);

        } catch (Exception e) {
            Log.e(TAG, "Error setting transport icon for mode: " + modeToUse, e);
            // Fallback to default icon on error
            try {
                this.transportModeIcon.setImageResource(R.drawable.ic_man_still);
            } catch (Exception fallbackEx) {
                Log.e(TAG, "Error setting fallback icon", fallbackEx);
                this.transportModeIcon.setVisibility(View.GONE); // Hide if fallback fails
            }
        }
    }

    /**
     * Updates various UI elements based on the selected tracking mode (Auto/Manual)
     * and the current tracking activity state.
     * NOTE: This method primarily handles the Switch and FAB visibility now.
     * The icon background/animation is handled by updateStartStopButtonState.
     * @param mode             The current tracking mode ("auto" or "manual").
     * @param isTrackingActive The current tracking state from MainActivity.
     */
    public void updateUiBasedOnTrackingMode(String mode, boolean isTrackingActive) {
        boolean isManual = MODE_MANUAL.equals(mode);
        Log.d(TAG, "UiUpdater.updateUiBasedOnTrackingMode called. Mode: " + mode + ", isTrackingActive = " + isTrackingActive);

        // Update Switch State and Text
        if (this.trackingModeSwitch != null) {
            this.trackingModeSwitch.setChecked(isManual);
            this.trackingModeSwitch.setText(isManual ? "Manual" : "Auto");
        } else { Log.w(TAG, "updateUiBasedOnTrackingMode: trackingModeSwitch is null"); }

        // Update FAB Visibility
        if (this.startStopFab != null) {
            this.startStopFab.setVisibility(isManual ? View.VISIBLE : View.GONE);
            Log.d(TAG, "updateUiBasedOnTrackingMode: FAB visibility set to: " + (isManual ? "VISIBLE" : "GONE"));
        } else { Log.w(TAG, "updateUiBasedOnTrackingMode: startStopFab is null"); }

        // Set UI State based on whether tracking is Active or Inactive
        if (!isTrackingActive) {
            // --- When tracking is INACTIVE ---
            Log.d(TAG, "updateUiBasedOnTrackingMode: Setting INACTIVE UI state.");
            // Set status label text
            updateTransportModeIcon(null); // Set default image resource

        } else {
            // --- When tracking becomes ACTIVE ---
            Log.d(TAG, "updateUiBasedOnTrackingMode: Setting ACTIVE UI state basics.");
            if (this.transportModeIcon != null) {
                this.transportModeIcon.setVisibility(View.VISIBLE); // Make visible
                this.transportModeIcon.setClickable(true);          // Make clickable
            }
        }
    } // *** CORRECTED closing brace location for updateUiBasedOnTrackingMode ***
} // End of UiUpdater class