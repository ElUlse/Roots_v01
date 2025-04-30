// Replace CurrentPolylineAnimator.java content with this:

package com.example.roots_d01;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// MapLibre Imports
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyFactory;

public class CurrentPolylineAnimator {

    private static final String TAG = "PolylineAnimator";
    private static final long BLINK_INTERVAL_MS = 600;
    private static final float OPACITY_OPAQUE = 1.0f;
    private static final float OPACITY_SEMI_TRANSPARENT = 0.3f;

    private final MapView mapView; // Keep reference if invalidate() is needed
    @NonNull // MapLibreMap should not be null after initialization
    private final MapLibreMap maplibreMap;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    private String targetLayerId; // ID of the LineLayer to animate
    private boolean isRunning = false;
    private boolean isCurrentlyFaded = false;

    private final Runnable blinkRunnable = new Runnable() {
        @Override
        public void run() {
            Log.v(TAG, ">>> blinkRunnable ENTERED"); // Should be the first line
            if (!isRunning || targetLayerId == null || maplibreMap == null) { // Check maplibreMap here
                Log.v(TAG, "BlinkRunnable stopping: isRunning=" + isRunning + ", targetLayerId=" + targetLayerId);
                // Ensure final state is opaque if we were running but target became null or stopped
                if (isRunning) stopBlinkingInternal(true);
                return;
            }

            try {
                Style style = maplibreMap.getStyle();
                if (style == null || !style.isFullyLoaded()) {
                    Log.w(TAG, "Style not available in blinkRunnable, skipping cycle.");
                    // Don't stop, just reschedule and try again later
                    if (isRunning) handler.postDelayed(this, BLINK_INTERVAL_MS);
                    return;
                }

                Layer layer = style.getLayer(targetLayerId);
                if (layer instanceof LineLayer) {
                    LineLayer lineLayer = (LineLayer) layer;
                    // ... (rest of opacity setting logic) ...
                    isCurrentlyFaded = !isCurrentlyFaded;
                    float newOpacity = isCurrentlyFaded ? OPACITY_SEMI_TRANSPARENT : OPACITY_OPAQUE;
                    lineLayer.setProperties(PropertyFactory.lineOpacity(newOpacity));
                    Log.v(TAG, "blinkRunnable: Opacity potentially set. Rescheduling."); // Keep this

                    if (isRunning) {
                        Log.v(TAG, "blinkRunnable: Posting self delay."); // Keep this
                        handler.postDelayed(this, BLINK_INTERVAL_MS);
                    }
                } else {
                    Log.w(TAG, "Target layer with ID '" + targetLayerId + "' not found or not a LineLayer in blinkRunnable, stopping blinker.");
                    stopBlinkingInternal(true); // Stop if layer is invalid
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during polyline blink runnable execution", e);
                stopBlinkingInternal(true);
            }
            Log.v(TAG, ">>> blinkRunnable EXITED"); // Should be the last line before end of run()
        }
    };

    // Constructor now takes MapLibreMap
    public CurrentPolylineAnimator(@NonNull MapView mapView, @NonNull MapLibreMap maplibreMap) {
        this.mapView = mapView; // Keep for potential invalidate()
        this.maplibreMap = maplibreMap;
    }

    public void setTargetLayerId(@Nullable String layerId) {
        Log.d(TAG, "Setting target layer ID: " + (layerId != null ? "'" + layerId + "'" : "null"));
        String oldTargetId = this.targetLayerId; // Store old ID before changing
        boolean wasRunning = this.isRunning; // Store if it was running

        this.targetLayerId = layerId; // Update the target ID

        // If it was running, stop it on the OLD target first, ensuring opacity reset
        if (wasRunning) {
            stopBlinkingOnTarget(oldTargetId, true); // Stop on old ID
        }

        // If the new target is null and it was running, ensure blinking loop stops
        if (layerId == null && wasRunning) {
            this.isRunning = false; // Explicitly stop the logical state
            handler.removeCallbacks(blinkRunnable);
            Log.d(TAG, "Target set to null while running, blink loop stopped.");
        }
    }


    public void startBlinking() {
        Log.i(TAG, ">>> startBlinking() called.");
        if (targetLayerId == null) {
            Log.w(TAG, "startBlinking: Cannot start, targetLayerId is null.");
            return;
        }
        if (maplibreMap == null) {
            Log.w(TAG, "startBlinking: Cannot start, maplibreMap is null.");
            return;
        }

        Style style = maplibreMap.getStyle();
        if (style == null || !style.isFullyLoaded()) {
            Log.w(TAG, "startBlinking: Cannot start, style not ready.");
            return;
        }

        Layer layer = style.getLayer(targetLayerId);
        if (!(layer instanceof LineLayer)) {
            Log.w(TAG, "startBlinking: Cannot start, target layer '" + targetLayerId + "' is not a LineLayer. Actual type: " + (layer != null ? layer.getClass().getName() : "null")); // Log actual type
            return;
        }


        if (isRunning) {
            Log.d(TAG, "Blinking already running for layer '" + targetLayerId + "', ensuring it starts opaque.");
            resetOpacity((LineLayer) layer); // Ensure it starts opaque if called again
            return; // Don't restart runnable if already going
        }

        Log.i(TAG, "Starting polyline opacity blinking for layer: '" + targetLayerId + "'"); // Info level
        isRunning = true;
        isCurrentlyFaded = false; // Start opaque
        resetOpacity((LineLayer) layer); // Set initial state

        handler.removeCallbacks(blinkRunnable);
        Log.d(TAG, "startBlinking: Posting blinkRunnable to handler."); // <-- ADD LOG
        handler.post(blinkRunnable);
    }

    /**
     * Gets the ID of the layer currently targeted by the animator.
     * @return The target layer ID, or null if none is set.
     */
    @Nullable // Indicates it might return null
    public String getTargetLayerId() {
        return this.targetLayerId;
    }


    public void stopBlinking() {
        stopBlinkingInternal(true); // Call internal helper, ensure opaque reset
    }

    // Internal stop method that acts on the current targetLayerId
    private void stopBlinkingInternal(boolean ensureOpaque) {
        stopBlinkingOnTarget(this.targetLayerId, ensureOpaque); // Delegate to target-specific method
        this.isRunning = false; // Make sure running flag is cleared
    }

    // Stops blinking and optionally resets opacity for a SPECIFIC layer ID
    private void stopBlinkingOnTarget(@Nullable String layerIdToStop, boolean ensureOpaque) {
        if (layerIdToStop == null) return; // No target to stop on

        // Only remove callbacks if this is the *currently running* target being stopped
        if (layerIdToStop.equals(this.targetLayerId) && isRunning) {
            Log.d(TAG, "Stopping polyline opacity blinking for layer: '" + layerIdToStop + "'");
            handler.removeCallbacks(blinkRunnable);
            // isRunning flag is cleared in the calling method (stopBlinking or setTargetLayerId)
        } else if (!layerIdToStop.equals(this.targetLayerId)) {
            Log.d(TAG, "Stopping blink effect requested for layer '" + layerIdToStop + "', which is not the current target ('" + this.targetLayerId + "'). Only resetting opacity.");
        }

        // Reset opacity if requested, regardless of whether it was the active blinking target
        if (ensureOpaque) {
            if (maplibreMap == null) {
                Log.w(TAG, "Could not reset opacity on stop: maplibreMap is null.");
                return;
            }
            Style style = maplibreMap.getStyle();
            if (style != null && style.isFullyLoaded()) {
                Layer layer = style.getLayer(layerIdToStop); // Get layer
                if (layer instanceof LineLayer) {
                    resetOpacity((LineLayer) layer); // Pass the valid LineLayer
                } else {
                    Log.w(TAG,"Could not reset opacity on stop: Layer '" + layerIdToStop + "' not found or not LineLayer.");
                }
            } else {
                Log.w(TAG,"Could not reset opacity on stop: Style not loaded.");
            }
        }
    }

    private void resetOpacity(@NonNull LineLayer lineLayer) {
        try {
            if (maplibreMap == null) {
                Log.w(TAG, "Cannot reset opacity: maplibreMap is null.");
                return;
            }
            lineLayer.setProperties(PropertyFactory.lineOpacity(OPACITY_OPAQUE));
            Log.v(TAG,"Reset layer '" + lineLayer.getId() + "' opacity to opaque.");
        } catch (Exception e) {
            Log.e(TAG, "Error resetting layer opacity for layer ID: " + lineLayer.getId(), e);
        }
    }
}