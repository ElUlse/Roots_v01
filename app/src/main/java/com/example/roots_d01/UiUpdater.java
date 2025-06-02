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

    private Animation pulseAnimation = null;

    private static final String MODE_AUTO = "auto";
    private static final String MODE_MANUAL = "manual";
    private static final String STATUS_AWAITING_MOVEMENT = "Awaiting Movement"; // Example text
    private static final String STATUS_MANUAL_MODE = "Manual Mode";


    // Constructor accepting all required UI elements
    // Constructor accepting all required UI elements
    public UiUpdater(Context context, Button gpsStatusButton, ImageView transportModeIcon,
                     FloatingActionButton startStopFab) {
        this.context = context.getApplicationContext();
        this.gpsStatusButton = gpsStatusButton;
        this.transportModeIcon = transportModeIcon;
        this.startStopFab = startStopFab;
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


    // In UiUpdater.java
    public void updateStartStopButtonState(boolean isGeneralTrackingActive, @Nullable String effectiveMode) {
        // Note: this.startStopFab in UiUpdater corresponds to manualStopFab in MainActivity

        if (this.startStopFab == null) {
            Log.w(TAG, "updateStartStopButtonState: startStopFab (manualStopFab) is null!");
            // Do not return early, as transportModeIcon might still need updating
        }

        // Determine if the FAB should be visible
        boolean isActuallyMoving = false;
        if (isGeneralTrackingActive) {
            isActuallyMoving = effectiveMode != null &&
                    !effectiveMode.equals("Still") &&
                    !effectiveMode.equals("Unknown");
        }

        // Set visibility and appearance of the FAB
        if (this.startStopFab != null) {
            if (isActuallyMoving) {
                this.startStopFab.setVisibility(View.VISIBLE);
                this.startStopFab.setImageResource(R.drawable.ic_stop); // Stop icon
                // Ensure a consistent stop color, e.g., using a color resource
                int stopColor = ContextCompat.getColor(context, R.color.md_theme_error); // Or any distinct color for stop
                this.startStopFab.setBackgroundTintList(ColorStateList.valueOf(stopColor));
                Log.d(TAG, "UiUpdater: Set manualStopFab VISIBLE (Tracking AND Moving). Mode: " + effectiveMode);
            } else {
                this.startStopFab.setVisibility(View.GONE);
                Log.d(TAG, "UiUpdater: Set manualStopFab GONE (Not Tracking or Not Moving). Tracking: " + isGeneralTrackingActive + ", Mode: " + effectiveMode);
            }
        }

        // --- Existing Transport Icon Background (Outline) & Animation ---
        // This part remains based on general tracking and if the mode indicates movement
        boolean showTransportIconAsActive = isGeneralTrackingActive && isActuallyMoving; // Simplified: show if tracking & moving

        if (this.transportModeIcon != null) {
            if (showTransportIconAsActive) {
                this.transportModeIcon.setVisibility(View.VISIBLE);
                Log.d(TAG, "updateStartStopButtonState: Set transport icon background to ACTIVE (with stroke)");

                if (this.transportModeIcon.getAnimation() == null) {
                    if (this.pulseAnimation == null) {
                        this.pulseAnimation = new AlphaAnimation(1.0f, 0.4f);
                        this.pulseAnimation.setDuration(700);
                        this.pulseAnimation.setRepeatMode(Animation.REVERSE);
                        this.pulseAnimation.setRepeatCount(Animation.INFINITE);
                    }
                    this.transportModeIcon.startAnimation(this.pulseAnimation);
                    Log.d(TAG, "updateStartStopButtonState: Started pulsing animation for transport icon");
                }
            } else {
                this.transportModeIcon.setVisibility(View.GONE);
                Log.d(TAG, "updateStartStopButtonState: Set transport icon background to INACTIVE (or hidden)");
                if (this.transportModeIcon.getAnimation() != null) {
                    this.transportModeIcon.clearAnimation();
                    this.transportModeIcon.setAlpha(1.0f); // Reset alpha
                    Log.d(TAG, "updateStartStopButtonState: Cleared pulsing animation for transport icon");
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