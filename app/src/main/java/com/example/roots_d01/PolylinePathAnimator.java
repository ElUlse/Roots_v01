package com.example.roots_d01;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import org.maplibre.geojson.Feature;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;

import java.util.List;

public class PolylinePathAnimator {

    private static final String TAG = "PolylinePathAnimator";
    // --- Constants (can be adjusted) ---
    private static final long ANIMATION_DURATION_MS = 10000; // 10 seconds
    private static final String ARROW_SOURCE_ID = "direction-arrow-source"; // Use same IDs as planned for MainActivity
    private static final String ARROW_LAYER_ID = "direction-arrow-layer";
    // Note: ARROW_ICON_ID is used when *creating* the layer, typically in MainActivity/MapManager

    private final MapLibreMap maplibreMap;
    private GeoJsonSource directionArrowSource;
    private SymbolLayer directionArrowLayer;

    private ValueAnimator animator = null;
    private LineString currentAnimatingLine = null;

    // Constructor requires the MapLibreMap instance
    public PolylinePathAnimator(@NonNull MapLibreMap map) {
        this.maplibreMap = map;
        // Source and Layer will be fetched/validated when animation starts
    }

    /**
     * Starts the animation along the provided LineString.
     * Stops any currently running animation first.
     * Ensures the arrow source and layer are available in the current map style.
     * @param line The LineString geometry to animate along.
     */
    public void startAnimation(@NonNull LineString line) {
        stopAnimation(); // Stop previous animation

        Style style = maplibreMap.getStyle();
        if (style == null || !style.isFullyLoaded()) {
            Log.w(TAG, "Cannot start animation: Style not ready.");
            return;
        }

        // --- Ensure Source and Layer are ready ---
        try {
            directionArrowSource = style.getSourceAs(ARROW_SOURCE_ID);
            directionArrowLayer = style.getLayerAs(ARROW_LAYER_ID);

            Log.d(TAG, "startAnimation: Found source? " + (directionArrowSource != null) + " Found layer? " + (directionArrowLayer != null));


            if (directionArrowSource == null || directionArrowLayer == null) {
                Log.e(TAG, "Cannot start animation: Arrow source or layer not found in style. Ensure they are added first.");
                // Attempt to create them? Or rely on MainActivity to have done it?
                // For now, we'll just log the error. Ensure setupAnimationLayer runs in MainActivity.
                return;
            }
        } catch(ClassCastException e){
            Log.e(TAG, "Arrow source/layer exists but is not the correct type (GeoJsonSource/SymbolLayer).", e);
            return;
        }

        if (line.coordinates().size() < 2) {
            Log.w(TAG, "Cannot start animation: Line has less than 2 points.");
            return;
        }

        Log.d(TAG, "Starting animation for line with " + line.coordinates().size() + " points.");
        currentAnimatingLine = line;

        // Make arrow visible and set initial position/rotation
        Log.d(TAG, "startAnimation: Setting arrow layer visibility to VISIBLE.");
        directionArrowLayer.setProperties(PropertyFactory.visibility(Property.VISIBLE));
        animateArrow(0f); // Update to starting position (fraction 0.0)

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            if (currentAnimatingLine != null) {
                float fraction = (float) valueAnimator.getAnimatedValue();
                animateArrow(fraction); // Update arrow based on fraction
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Log.d(TAG, "Animation ended.");
                // Keep arrow at the end, or hide it:
                // if (directionArrowLayer != null) {
                //    directionArrowLayer.setProperties(PropertyFactory.visibility(Property.NONE));
                // }
                animator = null;
                currentAnimatingLine = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                Log.d(TAG, "Animation cancelled.");
                if (directionArrowLayer != null) {
                    directionArrowLayer.setProperties(PropertyFactory.visibility(Property.NONE));
                }
                animator = null;
                currentAnimatingLine = null;
            }
        });
        animator.start();
    }

    /**
     * Stops the currently running animation and hides the arrow marker.
     */
    public void stopAnimation() {
        if (animator != null) { // Check animator directly
            if (animator.isRunning()) {
                animator.cancel(); // Triggers onAnimationCancel
            }
            animator = null; // Clear reference
        }
        // Ensure layer is hidden even if cancel listener didn't run somehow
        if (directionArrowLayer != null) {
            Style style = maplibreMap.getStyle();
            // Check style and layer again before setting properties
            if (style != null && style.isFullyLoaded() && style.getLayer(ARROW_LAYER_ID) != null) {
                directionArrowLayer.setProperties(PropertyFactory.visibility(Property.NONE));
            }
        }
        currentAnimatingLine = null; // Clear line reference
        Log.d(TAG, "stopAnimation() called.");
    }

    /**
     * Calculates the arrow's position and rotation for a given fraction
     * along the currentAnimatingLine and updates the map layer.
     * @param fraction Progress fraction (0.0 to 1.0).
     */
    // Replace the ENTIRE animateArrow method in PolylinePathAnimator.java

    private void animateArrow(float fraction) {
        // Log entry point
        Log.v(TAG, "animateArrow called with fraction: " + String.format("%.3f", fraction));

        // Check required objects *before* doing anything else
        if (currentAnimatingLine == null || directionArrowSource == null || directionArrowLayer == null) {
            Log.w(TAG, "animateArrow returning early: currentAnimatingLine=" + (currentAnimatingLine == null) +
                    ", directionArrowSource=" + (directionArrowSource == null) +
                    ", directionArrowLayer=" + (directionArrowLayer == null)); // Log which one is null
            return; // Exit if any are null
        }

        // --- Calculation ---
        List<Point> coordinates = currentAnimatingLine.coordinates();
        int nPoints = coordinates.size();
        if (nPoints < 2) {
            Log.w(TAG, "animateArrow returning early: Not enough points in line (" + nPoints + ")");
            stopAnimation(); // Stop if line becomes invalid
            return;
        }

        // ... (existing calculation logic for startVertexIndex, endVertexIndex, segmentFraction, lat, lon, currentPoint, bearing) ...
        float totalSegments = nPoints - 1;
        float targetSegmentIndexFloat = totalSegments * fraction;
        int startVertexIndex = (int) Math.floor(targetSegmentIndexFloat);
        startVertexIndex = Math.min(startVertexIndex, nPoints - 2);
        int endVertexIndex = startVertexIndex + 1;
        float segmentFraction = targetSegmentIndexFloat - startVertexIndex;
        Point startPoint = coordinates.get(startVertexIndex);
        Point endPoint = coordinates.get(endVertexIndex);
        double lat = startPoint.latitude() + (endPoint.latitude() - startPoint.latitude()) * segmentFraction;
        double lon = startPoint.longitude() + (endPoint.longitude() - startPoint.longitude()) * segmentFraction;
        Point currentPoint = Point.fromLngLat(lon, lat);
        double bearing = calculateBearing(startPoint, endPoint);
        // --- End Calculation ---


        // --- Update Map (inside try-catch) ---
        try {
            Style style = maplibreMap.getStyle(); // Get style again to be safe
            if (style != null && style.isFullyLoaded()) {
                directionArrowSource.setGeoJson(Feature.fromGeometry(currentPoint));
                directionArrowLayer.setProperties(PropertyFactory.iconRotate((float) bearing));
                // Log SUCCESS only after successful updates
                Log.v(TAG, "animateArrow: Successfully updated source/layer properties."); // <<< CORRECT LOG PLACEMENT
            } else {
                Log.w(TAG, "animateArrow: Style became invalid during update, cannot set properties.");
                stopAnimation(); // Stop if style is bad
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating arrow source/layer", e);
            stopAnimation(); // Stop if update fails
        }
    }

    // Helper method for manual bearing calculation
    private double calculateBearing(Point p1, Point p2) {
        double lat1 = Math.toRadians(p1.latitude());
        double lon1 = Math.toRadians(p1.longitude());
        double lat2 = Math.toRadians(p2.latitude());
        double lon2 = Math.toRadians(p2.longitude());
        double dLon = lon2 - lon1;
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double bearingRad = Math.atan2(y, x);
        return (Math.toDegrees(bearingRad) + 360) % 360;
    }
}