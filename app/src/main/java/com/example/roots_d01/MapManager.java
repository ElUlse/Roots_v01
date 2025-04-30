package com.example.roots_d01;

// --- MapLibre Imports ---
import org.maplibre.android.maps.MapView; // Use MapLibre's MapView
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
// *** NOTE: Use MapLibre GeoJSON classes ***
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;


// --- Android Imports ---
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
// Use androidx.preference if using PreferenceFragmentCompat
import android.util.Log;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;

import java.util.HashMap;
import java.util.Map;

public class MapManager {

    private static final String TAG = "MapManager";
    private static final String TAG_ROTATION = "MapRotationDebug";
    // Preferences constants (keep for saving/loading state)
    private static final String PREFS_NAME = "MapState";
    private static final String KEY_MAP_ZOOM = "mapZoomLevel";
    private static final String KEY_MAP_CENTER_LAT = "mapCenterLat";
    private static final String KEY_MAP_CENTER_LON = "mapCenterLon";
    private static final double DEFAULT_MAP_ZOOM = 14.0;
    private static final double DEFAULT_MAP_LAT = 51.5074; // London
    private static final double DEFAULT_MAP_LON = -0.1278; // London

    private final Context context;
    private final MapView mapView; // Reference to the MapLibre MapView from layout
    private MapLibreMap maplibreMap; // Reference to the core map object (set via initializeMapLibreComponents)
    private Style mapStyle;         // Reference to the loaded style (set via initializeMapLibreComponents)
    private final LocationManager locationManager;

    // --- MapLibre State for User Marker ---
    private GeoJsonSource userMarkerSource;
    private SymbolLayer userMarkerLayer;
    private static final String USER_MARKER_SOURCE_ID = "user-marker-source";
    private static final String USER_MARKER_LAYER_ID = "user-marker-layer";
    // Icon IDs (must match names used in addMarkerImagesToStyle)
    private static final String USER_ICON_STILL = "user-icon-still";
    private static final String USER_ICON_WALKING = "user-icon-walking";

    private static final String USER_ICON_BICYCLING = "user-icon-bicycling";
    private static final String USER_ICON_VEHICLE = "user-icon-vehicle";
    public static final String DEFAULT_STYLE_IDENTIFIER = "OSM Bright"; // Define a default


    // --- Sensor/Rotation State ---
    private OrientationListener orientationListener;
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private final SensorEventListener sensorEventListener;

    // Inside MapManager.java
    private static final Map<String, String> PREDEFINED_STYLE_URLS = new HashMap<>();

    static {
        PREDEFINED_STYLE_URLS.put("OSM Bright", "https://api.maptiler.com/maps/streets-v2/style.json?key=dnuxooBP2JxDA2AXS2AD");
        PREDEFINED_STYLE_URLS.put("Streets", "https://api.maptiler.com/maps/streets-v2/style.json?key=dnuxooBP2JxDA2AXS2AD"); // Replace key!
        PREDEFINED_STYLE_URLS.put("Outdoors", "https://api.maptiler.com/maps/outdoor-v2/style.json?key=dnuxooBP2JxDA2AXS2AD"); // Replace key!
        PREDEFINED_STYLE_URLS.put("Satellite Streets", "https://api.maptiler.com/maps/hybrid/style.json?key=dnuxooBP2JxDA2AXS2AD"); // Replace key!
        // Add other valid styles if needed
    }

    // Method to get the URL for a given identifier (name)
    public static String getStyleUrl(String identifier) {
        return PREDEFINED_STYLE_URLS.getOrDefault(identifier, PREDEFINED_STYLE_URLS.get(DEFAULT_STYLE_IDENTIFIER));
    }


    // Constructor
    public MapManager(Context context, MapView mapView, LocationManager locationManager) {
        this.context = context.getApplicationContext();
        this.mapView = mapView; // Store reference to MapLibre MapView
        this.locationManager = locationManager;

        // --- Sensor Initialization (Keep) ---
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (this.sensorManager != null) {
            this.rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (this.rotationVectorSensor == null) {
                Log.w(TAG, "Rotation Vector Sensor not available.");
            }
        } else {
            Log.e(TAG, "Device does not have SensorManager!");
            this.rotationVectorSensor = null;
        }
        this.sensorEventListener = createSensorEventListener();
    }

    // Method called from MainActivity AFTER MapLibreMap and Style are ready
    public void initializeMapLibreComponents(MapLibreMap map, Style style) {
        Log.d(TAG, "initializeMapLibreComponents called.");
        this.maplibreMap = map;
        this.mapStyle = style;

        if (map == null || style == null || !style.isFullyLoaded()) {
            Log.e(TAG, "Cannot initialize MapLibre components: Map or Style is null or not loaded.");
            return;
        }

        // Now setup components that depend on the map and style
        addMarkerImagesToStyle(style); // Add icons needed for markers first
        setupUserMarkerSourceAndLayer(style); // Create the source/layer for the user marker
    }

    // Add marker icon images to the map's style
    private void addMarkerImagesToStyle(@NonNull Style style) {
        Log.d(TAG, "Attempting to add marker images using AppCompatResources. Style loaded? " + style.isFullyLoaded());
        if (!style.isFullyLoaded()) {
            Log.w(TAG, "Style not ready in addMarkerImagesToStyle.");
            return;
        }

        // Define icons to load
        Map<String, Integer> iconsToLoad = new HashMap<>();
        iconsToLoad.put(USER_ICON_STILL, R.drawable.ic_man_still);
        iconsToLoad.put(USER_ICON_WALKING, R.drawable.ic_walking);
        iconsToLoad.put(USER_ICON_BICYCLING, R.drawable.ic_directions_bike);
        iconsToLoad.put(USER_ICON_VEHICLE, R.drawable.ic_directions_in_vehicle);

        iconsToLoad.put("direction-arrow-icon", R.drawable.ic_direction_arrow); // Make sure R.drawable.ic_direction_arrow exists!


        for (Map.Entry<String, Integer> entry : iconsToLoad.entrySet()) {
            String iconId = entry.getKey();
            int resourceId = entry.getValue();
            try {
                // 1. Load Drawable using AppCompatResources for vector compatibility
                Drawable drawable = AppCompatResources.getDrawable(context, resourceId);

                if (drawable != null) {
                    // 2. Create a Bitmap with appropriate dimensions
                    // Use intrinsic dimensions or set a fixed size if needed
                    int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 48; // Default width if needed
                    int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 48; // Default height if needed
                    Bitmap iconBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                    // 3. Draw the Drawable onto the Bitmap's Canvas
                    Canvas canvas = new Canvas(iconBitmap);
                    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    drawable.draw(canvas);

                    // 4. Add the generated Bitmap to the style
                    style.addImage(iconId, iconBitmap);
                    Log.d(TAG, "Successfully generated and added image: " + iconId);

                } else {
                    // Log an error if the Drawable couldn't be loaded
                    Log.e(TAG, "Failed to load Drawable for resource ID: " + resourceId + " (Icon ID: " + iconId + ")");
                }
            } catch (Exception e) {
                // Catch any other exceptions during drawable loading, bitmap creation, or adding
                Log.e(TAG, "Error processing/adding image: " + iconId + " with resource ID: " + resourceId, e);
            }
        }
        Log.d(TAG, "Finished attempting to add marker images.");
    }


    // Initialize the source and layer for the user marker
    // *** CORRECTED method name from 'c' ***
    private void setupUserMarkerSourceAndLayer(@NonNull Style style) {
        if (!style.isFullyLoaded()) {
            Log.w(TAG, "Style not ready in setupUserMarkerSourceAndLayer.");
            return;
        }
        Log.d(TAG, "Setting up user marker source and layer...");
        try {

            // 1. Create source WITHOUT initial geometry if it doesn't exist
            if (style.getSource(USER_MARKER_SOURCE_ID) == null) {
                // Using a placeholder point ensures the source is valid GeoJSON initially.
                // It will be updated immediately when the first location arrives.
                userMarkerSource = new GeoJsonSource(USER_MARKER_SOURCE_ID,
                        Feature.fromGeometry(Point.fromLngLat(0.0, 0.0))); // Placeholder
                style.addSource(userMarkerSource);
                Log.d(TAG, "Added user marker source with placeholder geometry.");
            } else {
                userMarkerSource = style.getSourceAs(USER_MARKER_SOURCE_ID); // Get existing source
                // Ensure the member variable is assigned even if source exists
                if (userMarkerSource == null) {
                    Log.e(TAG, "Source " + USER_MARKER_SOURCE_ID + " exists but is not a GeoJsonSource!");
                    return; // Cannot proceed if type is wrong
                }
                Log.d(TAG, "Found existing user marker source.");
            }


            // 2. Create and add the SymbolLayer (if it doesn't exist)
            if (style.getLayer(USER_MARKER_LAYER_ID) == null) {
                userMarkerLayer = new SymbolLayer(USER_MARKER_LAYER_ID, USER_MARKER_SOURCE_ID)
                        .withProperties(
                                PropertyFactory.iconImage(USER_ICON_STILL), // Default icon
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
                                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                                PropertyFactory.iconSize(1.0f)
                                // Initially, let's not rotate it until bearing is known
                                // PropertyFactory.iconRotate(0f)
                        );

                // Add the layer to the map
                style.addLayer(userMarkerLayer);
                Log.d(TAG, "Added user marker layer.");
            } else {
                userMarkerLayer = style.getLayerAs(USER_MARKER_LAYER_ID); // Get existing layer
                // Ensure the member variable is assigned even if layer exists
                if (userMarkerLayer == null) {
                    Log.e(TAG, "Layer " + USER_MARKER_LAYER_ID + " exists but is not a SymbolLayer!");
                    return; // Cannot proceed if type is wrong
                }
                Log.d(TAG, "Found existing user marker layer.");
            }

            // *** REMOVED duplicated/misplaced block related to initialFeature ***

        } catch (Exception e) {
            Log.e(TAG, "Error setting up user marker source/layer", e);
        }
    }


    // Restore map state (Zoom/Center) - Called from MainActivity.onMapReady
    public void restoreMapState(MapLibreMap map, @Nullable LatLng initialLatLng) {
        if (map == null) {
            Log.e(TAG, "MapLibreMap is null in restoreMapState");
            return;
        }
        Log.d(TAG, "MapManager: Restoring map state...");

        // --- Determine Default Location ---
        // Use provided initialLatLng if available, otherwise default to London
        double defaultLat = (initialLatLng != null) ? initialLatLng.getLatitude() : DEFAULT_MAP_LAT;
        double defaultLon = (initialLatLng != null) ? initialLatLng.getLongitude() : DEFAULT_MAP_LON;
        Log.d(TAG, "restoreMapState: Using default coordinates: Lat=" + defaultLat + ", Lon=" + defaultLon);
        // --- End Determine Default Location ---

        try {
            SharedPreferences mapStatePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            // Use the determined defaults when reading from prefs
            double loadedZoom = mapStatePrefs.getFloat(KEY_MAP_ZOOM, (float) DEFAULT_MAP_ZOOM);
            double loadedLat = Double.longBitsToDouble(mapStatePrefs.getLong(KEY_MAP_CENTER_LAT, Double.doubleToRawLongBits(defaultLat)));
            double loadedLon = Double.longBitsToDouble(mapStatePrefs.getLong(KEY_MAP_CENTER_LON, Double.doubleToRawLongBits(defaultLon)));

            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(loadedLat, loadedLon), loadedZoom));
            Log.d(TAG, "MapManager: Restored state to Lat: " + loadedLat + ", Lon: " + loadedLon + ", Zoom: " + loadedZoom);
        } catch (Exception e) {
            Log.e(TAG, "Error restoring map state", e);
            // Apply determined default state on error
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(defaultLat, defaultLon), DEFAULT_MAP_ZOOM));
        }
    }

    // Overload for backward compatibility or cases where initial location isn't needed/available
    public void restoreMapState(MapLibreMap map) {
        restoreMapState(map, null); // Call the main method with null initialLatLng
    }

    // Save map state (Zoom/Center) - Called from MainActivity.onStop
    public void saveMapState() { // No longer needs SharedPreferences argument
        if (maplibreMap == null) {
            Log.w(TAG, "saveMapState: maplibreMap is null.");
            return;
        }
        Log.d(TAG, "MapManager: Saving map state...");
        try {
            SharedPreferences mapStatePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = mapStatePrefs.edit();
            LatLng center = maplibreMap.getCameraPosition().target;
            double zoom = maplibreMap.getCameraPosition().zoom;
            editor.putLong(KEY_MAP_CENTER_LAT, Double.doubleToRawLongBits(center.getLatitude()));
            editor.putLong(KEY_MAP_CENTER_LON, Double.doubleToRawLongBits(center.getLongitude()));
            editor.putFloat(KEY_MAP_ZOOM, (float) zoom);
            editor.apply();
            Log.d(TAG, "MapManager: Saved state Lat: " + center.getLatitude() + ", Lon: " + center.getLongitude() + ", Zoom: " + zoom);
        } catch (Exception e) {
            Log.e(TAG, "Error saving map state", e);
        }
    }

    // Recenter map to last known location or user marker position
    public void recenterMap() {
        if (maplibreMap == null) {
            Log.w(TAG, "Recenter button clicked, but maplibreMap is null.");
            return;
        }

        LatLng targetPoint = null;

        // --- Try getting position from the marker source ---
        // Getting the geometry directly from the source requires parsing its GeoJSON
        // It's often easier to get the last known location from the system.
        // If you *need* the marker's current position from the map:
        /*
        if (userMarkerSource != null) {
            try {
                 // This assumes the source has a single Point feature
                 FeatureCollection fc = FeatureCollection.fromJson(userMarkerSource.toJson());
                 if (fc != null && fc.features() != null && !fc.features().isEmpty()) {
                     Point currentPoint = (Point) fc.features().get(0).geometry();
                     if (currentPoint != null) {
                         targetPoint = new LatLng(currentPoint.latitude(), currentPoint.longitude());
                         Log.d(TAG, "Recenter target from User Marker Source: " + targetPoint);
                     }
                 }
            } catch (Exception e) {
                Log.e(TAG, "Error getting position from userMarkerSource", e);
            }
        }
        */

        // --- Fallback to last known location (Preferable) ---
        if (targetPoint == null && ActivityCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && this.locationManager != null) {
            try {
                Location loc = this.locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc == null) {
                    loc = this.locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                if (loc != null) {
                    targetPoint = new LatLng(loc.getLatitude(), loc.getLongitude());
                    Log.d(TAG, "Recenter target from LocationManager: " + targetPoint);
                }
            } catch (SecurityException e) { Log.e(TAG, "Permission error getting location for recenter.", e); }
            catch (Exception e) { Log.e(TAG, "Error getting last known location for recenter.", e); }
        }

        // --- Animate camera ---
        if (targetPoint != null) {
            maplibreMap.animateCamera(CameraUpdateFactory.newLatLng(targetPoint), 1000); // Animate over 1 second
            Log.d(TAG, "Animating map recenter to: " + targetPoint);
        } else {
            Toast.makeText(this.context, "Current location unavailable.", Toast.LENGTH_SHORT).show();
        }
    }


    // --- User Marker Methods (Refactored for MapLibre) ---

    // Method called from initializeMapLibreComponents
    private void createUserMarker(Style style) {
        if (style == null || !style.isFullyLoaded()) {
            Log.w(TAG,"Cannot create user marker, style not ready.");
            return;
        }
        if (style.getSource(USER_MARKER_SOURCE_ID) == null || style.getLayer(USER_MARKER_LAYER_ID) == null) {
            Log.w(TAG,"User marker source/layer missing, attempting to set up...");
            setupUserMarkerSourceAndLayer(style); // Let setup handle creation
        } else {
            Log.d(TAG,"User marker source/layer already exist.");
        }
    }

    // Update user marker position and icon
    public void updateUserLocationMarker(LatLng location, String transportMode) {
        // Check if source/layer are ready (they should be if initializeMapLibreComponents was called)
        if (userMarkerSource == null || userMarkerLayer == null || mapStyle == null || !mapStyle.isFullyLoaded()) {
            Log.w(TAG, "Cannot update user marker: Source, Layer, or Style not ready/initialized.");
            // Attempt to initialize if style is ready but source/layer are missing
            if (mapStyle != null && mapStyle.isFullyLoaded() && (userMarkerSource == null || userMarkerLayer == null)) {
                Log.w(TAG, "Attempting late initialization of user marker source/layer.");
                // Avoid calling initializeMapLibreComponents directly here to prevent loops
                // Let's try setting up just the marker source/layer again
                setupUserMarkerSourceAndLayer(mapStyle);
                // Re-check after attempt
                if (userMarkerSource == null || userMarkerLayer == null) {
                    Log.e(TAG, "Late initialization of marker source/layer failed.");
                    return;
                }
            } else {
                return; // Still not ready
            }
        }
        Log.v("MarkerDebug", "MapManager.updateUserLocationMarker ENTERED. LatLng: " + location.toString() + ", Mode: " + transportMode);

        try {
            // 1. Create Point geometry and Feature using MapLibre GeoJSON classes
            Point pointGeometry = Point.fromLngLat(location.getLongitude(), location.getLatitude());
            Feature pointFeature = Feature.fromGeometry(pointGeometry);

            // 2. Set the GeoJSON Feature directly on the source
            userMarkerSource.setGeoJson(pointFeature); // Pass the Feature object
            Log.v("MarkerDebug", "Updated user marker source geometry with Feature.");

            // 3. Update Layer Icon
            String iconId = getIconIdForMode(transportMode);
            userMarkerLayer.setProperties(PropertyFactory.iconImage(iconId));
            Log.v("MarkerDebug", "Set user marker layer icon to: " + iconId);

        } catch (Exception e) {
            Log.e(TAG, "Error updating user marker source/layer", e);
        }
    }

    // Helper to get the correct icon ID string based on mode
    private String getIconIdForMode(String transportMode) {
        if (transportMode == null) return USER_ICON_STILL;
        switch (transportMode) {
            case "Walking":     return USER_ICON_WALKING;
            case "Bicycling":   return USER_ICON_BICYCLING;
            case "In Vehicle":  return USER_ICON_VEHICLE;
            case "Still":
            case "Unknown":
            default:            return USER_ICON_STILL;
        }
    }


    // --- Sensor and Rotation Handling (Refactored for MapLibre) ---

    // Creates the sensor event listener implementation
    private SensorEventListener createSensorEventListener() {
        return new SensorEventListener() {
            private final float[] rotationMatrix = new float[9];
            private final float[] orientationAngles = new float[3];

            @Override
            public void onSensorChanged(SensorEvent event) {
                if (maplibreMap == null || event.sensor == null || event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) { // Add null check for sensor
                    return;
                }

                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientationAngles);
                float azimuthDegrees = (float) Math.toDegrees(orientationAngles[0]);
                float currentBearing = (azimuthDegrees + 360) % 360;

                // --- Notify MainActivity about potential orientation change ---
                float currentMapBearing = (float) maplibreMap.getCameraPosition().bearing;
                currentMapBearing = (currentMapBearing + 360) % 360;
                boolean isRotated = Math.abs(currentMapBearing) > 5.0f; // Check if map isn't North-up

                if (orientationListener != null) {
                    orientationListener.onOrientationChanged(isRotated);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        };
    }

    // Register sensor listener
    public void registerSensorListener() {
        if (sensorManager != null && rotationVectorSensor != null && sensorEventListener != null) {
            boolean registered = sensorManager.registerListener(sensorEventListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
            Log.d(TAG_ROTATION, "Registering sensor listener... Success? " + registered);
        } else {
            Log.w(TAG, "Cannot register sensor listener - Manager, Sensor, or Listener not available.");
        }
    }

    // Unregister sensor listener
    public void unregisterSensorListener() {
        if (sensorManager != null && sensorEventListener != null) {
            try { // Add try-catch for safety
                sensorManager.unregisterListener(sensorEventListener);
                Log.d(TAG_ROTATION, "Sensor listener unregistered.");
            } catch (Exception e) {
                Log.e(TAG_ROTATION, "Error unregistering sensor listener", e);
            }
        }
    }

    // Set the listener for orientation changes
    public void setOrientationListener(OrientationListener listener) {
        this.orientationListener = listener;
        Log.d(TAG_ROTATION, "OrientationListener set in MapManager: " + (listener != null));
    }

    // Interface for orientation change callbacks
    public interface OrientationListener {
        void onOrientationChanged(boolean isRotated);
    }

    // Method for MainActivity to check current orientation and notify listener
    public void checkAndNotifyOrientation(double currentMapBearing) {
        if (orientationListener != null) {
            float bearing = (float) currentMapBearing;
            bearing = (bearing + 360) % 360; // Normalize
            boolean isRotated = Math.abs(bearing) > 5.0f;
            orientationListener.onOrientationChanged(isRotated);
            Log.d(TAG_ROTATION, "checkAndNotifyOrientation: Notified listener. isRotated = " + isRotated);
        }
    }

} // End MapManager
