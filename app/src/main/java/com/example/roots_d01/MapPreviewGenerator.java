package com.example.roots_d01;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Log;
import java.util.List;

public class MapPreviewGenerator {

    private static final String TAG = "MapPreviewGenerator";

    /**
     * Generates a preview bitmap with the polyline drawn on a plain background.
     * This is a fallback or for cases where a basemap isn't needed.
     *
     * @param points         The list of PolylinePoints representing the journey.
     * @param widthPx        The desired width of the output bitmap in pixels.
     * @param heightPx       The desired height of the output bitmap in pixels.
     * @param polylineColor  The color of the polyline.
     * @return A Bitmap with the polyline drawn, or null on error/invalid input.
     */
    public static Bitmap generatePreviewBitmap(List<PolylinePoint> points, int widthPx, int heightPx, int polylineColor) {
        if (points == null || points.size() < 2 || widthPx <= 0 || heightPx <= 0) {
            Log.w(TAG, "generatePreviewBitmap (original): Invalid input. Points: " + (points == null ? "null" : points.size()) + ", Width: " + widthPx + ", Height: " + heightPx);
            return null;
        }
        Log.d(TAG, "generatePreviewBitmap (original): Generating preview for " + points.size() + " points on new bitmap (" + widthPx + "x" + heightPx + ").");

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.parseColor("#E0E0E0")); // Light gray background

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (PolylinePoint p : points) {
            minLat = Math.min(minLat, p.latitude);
            maxLat = Math.max(maxLat, p.latitude);
            minLon = Math.min(minLon, p.longitude);
            maxLon = Math.max(maxLon, p.longitude);
        }

        double latSpan = maxLat - minLat;
        double lonSpan = maxLon - minLon;
        double paddingFactor = 0.1; // 10% padding

        if (latSpan < 0.00001) latSpan = 0.0002;
        if (lonSpan < 0.00001) lonSpan = 0.0002;

        minLat -= latSpan * paddingFactor;
        maxLat += latSpan * paddingFactor;
        minLon -= lonSpan * paddingFactor;
        maxLon += lonSpan * paddingFactor;

        latSpan = maxLat - minLat;
        lonSpan = maxLon - minLon;

        if (latSpan <= 0.000001 || lonSpan <= 0.000001) {
            Log.w(TAG, "generatePreviewBitmap (original): Lat/Lon span too small after padding. Returning blank bitmap. LatSpan: " + latSpan + ", LonSpan: " + lonSpan);
            return bitmap;
        }

        Paint paint = new Paint();
        paint.setColor(polylineColor);
        paint.setStrokeWidth(Math.max(2f, widthPx / 100f));
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        Path path = new Path();
        boolean firstPoint = true;
        for (PolylinePoint p : points) {
            float x = (float) ((p.longitude - minLon) / lonSpan) * widthPx;
            float y = heightPx - (float) ((p.latitude - minLat) / latSpan) * heightPx; // Y inverted
            if (firstPoint) {
                path.moveTo(x, y);
                firstPoint = false;
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, paint);
        Log.d(TAG, "generatePreviewBitmap (original): Finished drawing on new bitmap.");
        return bitmap;
    }

    /**
     * Draws a polyline onto an existing basemap Bitmap.
     * The points are scaled to fit the dimensions of the basemapBitmap,
     * using a bounding box derived from the points themselves with padding.
     *
     * @param basemapBitmap  The Bitmap of the map to draw upon. This bitmap will be modified.
     * @param points         The list of PolylinePoints representing the journey.
     * @param polylineColor  The color of the polyline.
     * @param polylineWidthPx The width of the polyline stroke in pixels.
     * @return The modified basemapBitmap with the polyline drawn on it, or the original basemapBitmap on error/invalid input.
     */
    public static Bitmap drawPolylineOnBasemap(Bitmap basemapBitmap, List<PolylinePoint> points, int polylineColor, float polylineWidthPx) {
        if (basemapBitmap == null) {
            Log.w(TAG, "drawPolylineOnBasemap: Basemap bitmap is null.");
            return null;
        }
        if (points == null || points.size() < 2) {
            Log.w(TAG, "drawPolylineOnBasemap: Not enough points to draw a polyline (< 2). Returning original basemap.");
            return basemapBitmap;
        }

        int widthPx = basemapBitmap.getWidth();
        int heightPx = basemapBitmap.getHeight();
        Log.d(TAG, "drawPolylineOnBasemap: Drawing on basemap (" + widthPx + "x" + heightPx + ") for " + points.size() + " points.");

        try {
            Bitmap mutableBasemap = basemapBitmap;
            if (!basemapBitmap.isMutable()) {
                mutableBasemap = basemapBitmap.copy(Bitmap.Config.ARGB_8888, true);
                Log.d(TAG, "drawPolylineOnBasemap: Created mutable copy of basemap.");
            }

            Canvas canvas = new Canvas(mutableBasemap);

            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
            double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            for (PolylinePoint p : points) {
                minLat = Math.min(minLat, p.latitude);
                maxLat = Math.max(maxLat, p.latitude);
                minLon = Math.min(minLon, p.longitude);
                maxLon = Math.max(maxLon, p.longitude);
            }

            double latSpan = maxLat - minLat;
            double lonSpan = maxLon - minLon;
            double paddingFactor = 0.10;

            if (latSpan < 0.00001) latSpan = 0.0002;
            if (lonSpan < 0.00001) lonSpan = 0.0002;

            minLat -= latSpan * paddingFactor;
            maxLat += latSpan * paddingFactor;
            minLon -= lonSpan * paddingFactor;
            maxLon += lonSpan * paddingFactor;

            latSpan = maxLat - minLat;
            lonSpan = maxLon - minLon;

            if (latSpan <= 0.000001 || lonSpan <= 0.000001) {
                Log.w(TAG, "drawPolylineOnBasemap: Lat/Lon span too small after padding. Returning original basemap.");
                return mutableBasemap;
            }

            Paint paint = new Paint();
            paint.setColor(polylineColor);
            paint.setStrokeWidth(polylineWidthPx);
            paint.setStyle(Paint.Style.STROKE);
            paint.setAntiAlias(true);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            Path path = new Path();
            boolean firstPoint = true;
            for (PolylinePoint p : points) {
                float x = (float) ((p.longitude - minLon) / lonSpan) * widthPx;
                float y = heightPx - (float) ((p.latitude - minLat) / latSpan) * heightPx; // Y inverted
                if (firstPoint) {
                    path.moveTo(x, y);
                    firstPoint = false;
                } else {
                    path.lineTo(x, y);
                }
            }
            canvas.drawPath(path, paint);
            Log.d(TAG, "drawPolylineOnBasemap: Successfully drew polyline on basemap.");
            return mutableBasemap;

        } catch (Exception e) {
            Log.e(TAG, "drawPolylineOnBasemap: Error drawing polyline on basemap", e);
            return basemapBitmap;
        }
    }
}
