package com.example.roots_d01;

import android.util.Log;
import androidx.annotation.NonNull;

import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.HeatmapLayer;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.FeatureCollection;

import java.util.ArrayList;

import static org.maplibre.android.style.expressions.Expression.*; // Static imports for expressions

public class HeatmapToggleManager {

    private static final String TAG = "HeatmapToggleManager";
    private static final String HEATMAP_SOURCE_ID = "heatmap-source";
    private static final String HEATMAP_LAYER_ID = "heatmap-layer";
    // Prefix for historical journey layers (must match MainActivity)
    private static final String HISTORICAL_LAYER_PREFIX = "historical-journey-layer-";

    private final MapLibreMap maplibreMap;
    private FeatureCollection heatmapFeatureCollection = FeatureCollection.fromFeatures(new ArrayList<>());
    private boolean heatmapDataReady = false;

    public HeatmapToggleManager(@NonNull MapLibreMap map) {
        this.maplibreMap = map;
    }

    /**
     * Creates the heatmap source and layer if they don't exist.
     * Should be called once when the map style is loaded.
     *
     * @param style The loaded map style.
     */
    public void setupHeatmapSourceLayer(@NonNull Style style) {
        Log.d(TAG, "Setting up heatmap layer and source...");
        if (style.getSource(HEATMAP_SOURCE_ID) == null) {
            GeoJsonSource heatmapSource = new GeoJsonSource(HEATMAP_SOURCE_ID,
                    FeatureCollection.fromFeatures(new ArrayList<>())); // Start empty
            style.addSource(heatmapSource);
            Log.i(TAG, "Added heatmap source: " + HEATMAP_SOURCE_ID);
        }

        if (style.getLayer(HEATMAP_LAYER_ID) == null) {
            HeatmapLayer heatmapLayer = new HeatmapLayer(HEATMAP_LAYER_ID, HEATMAP_SOURCE_ID);
            heatmapLayer.setMaxZoom(18); // Keep max zoom
            heatmapLayer.setProperties(
                    // *** ADD heatmapWeight Property ***
                    PropertyFactory.heatmapWeight(
                            // Get the "weight" property from the Feature, fallback to 1 if missing
                            coalesce(get("weight"), literal(1))
                    ),
                    // --- Adjust Intensity and Radius ---
                    // You might need to DECREASE intensity or radius now that weights are used,
                    // otherwise high-frequency spots might become too large/intense. Experiment!
                    PropertyFactory.heatmapIntensity(literal(0.6f)), // Use a literal float value for now
                    // Or try a fixed intensity: literal(0.5f)
                    PropertyFactory.heatmapRadius(literal(15f)), // Set a fixed pixel radius (e.g., 15 pixels). Adjust this value as needed.

                    PropertyFactory.heatmapColor(
                            interpolate( // Color ramp can remain similar initially
                                    linear(), heatmapDensity(),
                                    literal(0.01), rgba(0, 0, 255, 0.0), // Transparent blue
                                    literal(0.35), rgb(0, 255, 255),   // Cyan (was 0.25)
                                    literal(0.65), rgb(0, 255, 0),    // Green (was 0.5)
                                    literal(0.85), rgb(255, 255, 0),  // Yellow (was 0.75)
                                    literal(1.0),  rgb(255, 0, 0)     // Red
                            )
                    ),
                    PropertyFactory.heatmapOpacity(0.7f), // Opacity remains the same
                    PropertyFactory.visibility(Property.NONE) // Initially hidden
            );

            // Layer placement logic remains the same
            String labelLayerId = findFirstLabelLayerId(style);
            if (labelLayerId != null) {
                style.addLayerBelow(heatmapLayer, labelLayerId);
                Log.i(TAG, "Added heatmap layer '" + HEATMAP_LAYER_ID + "' below '" + labelLayerId + "' (with weight).");
            } else {
                style.addLayer(heatmapLayer);
                Log.w(TAG, "Added heatmap layer '" + HEATMAP_LAYER_ID + "' on top (with weight).");
            }
        } else {
            Log.d(TAG, "Heatmap layer already exists.");
            // If it exists, we might want to update its properties to include weight if it wasn't there before
            Layer layer = style.getLayer(HEATMAP_LAYER_ID);
            if (layer instanceof HeatmapLayer) {
                ((HeatmapLayer)layer).setProperties(
                        PropertyFactory.heatmapWeight(coalesce(get("weight"), literal(1))), // <--- CORRECTED
                        PropertyFactory.visibility(Property.NONE)// Ensure still hidden initially
                        // Optionally re-apply intensity/radius/color if you changed them above
                );
                Log.d(TAG, "Updated existing heatmap layer to use weight property.");
            }
            // Ensure it's hidden if it already existed (original logic)
            // Layer layer = style.getLayer(HEATMAP_LAYER_ID); // Already got layer above
            // if(layer != null) layer.setProperties(PropertyFactory.visibility(Property.NONE)); // Handled above
        }
    }

    /**
     * Updates the data used by the heatmap source.
     * @param features The FeatureCollection containing all points.
     */
    public void updateHeatmapData(@NonNull FeatureCollection features) { // <-- Corrected (no semicolon)
        this.heatmapFeatureCollection = features;
        this.heatmapDataReady = true;
        Log.i(TAG, "Heatmap data updated with " + features.features().size() + " features.");

        // Update the source immediately if the map style is ready
        Style style = maplibreMap.getStyle();
        if (style != null && style.isFullyLoaded()) {
            GeoJsonSource heatmapSource = style.getSourceAs(HEATMAP_SOURCE_ID);
            if (heatmapSource != null) {
                Log.d(TAG, "Applying updated data to heatmap source.");
                heatmapSource.setGeoJson(heatmapFeatureCollection);
            } else {
                Log.w(TAG, "Heatmap source (" + HEATMAP_SOURCE_ID + ") not found while updating data.");
            }
        }
    }

    /**
     * Sets the map display mode (Heatmap or Polylines).
     * @param showHeatmap True to show heatmap and hide polylines, False otherwise.
     * @param journeyLayerCount The number of historical journey polyline layers to manage.
     */
    public void setDisplayMode(boolean showHeatmap, int journeyLayerCount) {
        // Get style asynchronously
        maplibreMap.getStyle(style -> { // <<< START getStyle lambda
            Log.d(TAG, "Setting display mode (in callback): showHeatmap=" + showHeatmap);
            try {
                HeatmapLayer heatmapLayer = style.getLayerAs(HEATMAP_LAYER_ID);

                if (showHeatmap) {
                    if (heatmapDataReady) {
                        GeoJsonSource heatmapSource = style.getSourceAs(HEATMAP_SOURCE_ID);
                        if (heatmapSource != null) {
                            heatmapSource.setGeoJson(heatmapFeatureCollection);
                            Log.d(TAG,"Updated heatmap source data before showing.");
                        }
                    } else { /* Log warning */ }

                    if (heatmapLayer != null) {
                        heatmapLayer.setProperties(PropertyFactory.visibility(Property.VISIBLE));
                        Log.d(TAG, "Set heatmap layer VISIBLE.");
                    }
                    // Pass style object to helper
                    hideAllHistoricalPolylinesInternal(style, journeyLayerCount);

                } else {
                    if (heatmapLayer != null) {
                        heatmapLayer.setProperties(PropertyFactory.visibility(Property.NONE));
                        Log.d(TAG, "Set heatmap layer GONE.");
                    }
                    // Pass style object to helper
                    showAllHistoricalPolylinesInternal(style, journeyLayerCount);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting display mode within getStyle callback", e);
            }
        }); // <<< END getStyle lambda
    }

    // Add INTERNAL helpers that take the Style object
    private void hideAllHistoricalPolylinesInternal(@NonNull Style style, int journeyLayerCount) {
        Log.d(TAG, "Hiding " + journeyLayerCount + " historical polyline layers...");
        for (int i = 0; i < journeyLayerCount; i++) {
            String layerId = HISTORICAL_LAYER_PREFIX + i;
            Layer layer = style.getLayer(layerId);
            if (layer != null) { layer.setProperties(PropertyFactory.visibility(Property.NONE)); }
        }
        Log.d(TAG, "Finished hiding historical polyline layers.");
    }

    private void showAllHistoricalPolylinesInternal(@NonNull Style style, int journeyLayerCount) {
        Log.d(TAG, "Showing " + journeyLayerCount + " historical polyline layers...");
        for (int i = 0; i < journeyLayerCount; i++) {
            String layerId = HISTORICAL_LAYER_PREFIX + i;
            Layer layer = style.getLayer(layerId);
            if (layer != null) { layer.setProperties(PropertyFactory.visibility(Property.VISIBLE)); }
            else { Log.w(TAG,"Layer "+layerId+" not found while trying to show."); }
        }
        Log.d(TAG, "Finished showing historical polyline layers.");
    }

    // --- Private Helper Methods ---


    private String findFirstLabelLayerId(Style style) {
        // Prioritize finding common label layers
        String[] commonLabelLayerIds = {"road-label", "highway-label", "place-label", "poi-label", "waterway-label"};
        for (String id : commonLabelLayerIds) {
            if (style.getLayer(id) instanceof SymbolLayer) {
                return id;
            }
        }
        // Fallback: find any symbol layer containing "label"
        for (Layer layer : style.getLayers()) {
            if (layer instanceof SymbolLayer && layer.getId().contains("label")) {
                return layer.getId();
            }
        }
        // Fallback: find the first symbol layer as a last resort
        for (Layer layer : style.getLayers()) {
            if (layer instanceof SymbolLayer) {
                Log.w(TAG, "Using fallback first SymbolLayer ID: " + layer.getId());
                return layer.getId();
            }
        }
        Log.w(TAG, "Could not find any suitable label/symbol layer to place heatmap below.");
        return null;
    }
}