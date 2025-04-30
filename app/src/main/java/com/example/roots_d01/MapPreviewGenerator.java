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

    // Generates a bitmap showing the polyline shape
    public static Bitmap generatePreviewBitmap(List<PolylinePoint> points, int widthPx, int heightPx, int polylineColor) {
        if (points == null || points.size() < 2 || widthPx <= 0 || heightPx <= 0) {
            return null;
        }

        try {
            // 1. Calculate Bounding Box
            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
            double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            for (PolylinePoint p : points) {
                minLat = Math.min(minLat, p.latitude);
                maxLat = Math.max(maxLat, p.latitude);
                minLon = Math.min(minLon, p.longitude);
                maxLon = Math.max(maxLon, p.longitude);
            }

            // Handle case where points are identical (or almost)
            double latSpan = maxLat - minLat;
            double lonSpan = maxLon - minLon;
            if (latSpan == 0 && lonSpan == 0) { // All points are the same
                // Draw a small dot maybe? For now, return null.
                Log.w(TAG, "Cannot generate preview, all points are identical.");
                return null;
            }

            // Add some padding to the bounds if spans are very small
            double paddingFactor = 0.1; // 10% padding
            if (latSpan == 0) latSpan = Math.abs(maxLat * 0.0001); // Add tiny span if needed
            if (lonSpan == 0) lonSpan = Math.abs(maxLon * 0.0001); // Add tiny span if needed

            minLat -= latSpan * paddingFactor;
            maxLat += latSpan * paddingFactor;
            minLon -= lonSpan * paddingFactor;
            maxLon += lonSpan * paddingFactor;

            latSpan = maxLat - minLat; // Recalculate spans with padding
            lonSpan = maxLon - minLon;


            // 2. Create Bitmap and Canvas
            Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE); // Or a light gray background Color.parseColor("#F0F0F0")

            // 3. Prepare Paint
            Paint paint = new Paint();
            paint.setColor(polylineColor);
            paint.setStrokeWidth(3f); // Adjust stroke width as needed
            paint.setStyle(Paint.Style.STROKE);
            paint.setAntiAlias(true);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            // 4. Create and Scale Path
            Path path = new Path();
            boolean firstPoint = true;

            for (PolylinePoint p : points) {
                // Scale lat/lon to bitmap coordinates (simple linear scaling)
                // Handle potential division by zero if span is somehow still zero
                float x = (lonSpan == 0) ? widthPx / 2f : (float) ((p.longitude - minLon) / lonSpan) * widthPx;
                // Y coordinate is inverted because canvas Y increases downwards
                float y = (latSpan == 0) ? heightPx / 2f : heightPx - (float) ((p.latitude - minLat) / latSpan) * heightPx;


                if (firstPoint) {
                    path.moveTo(x, y);
                    firstPoint = false;
                } else {
                    path.lineTo(x, y);
                }
            }

            // 5. Draw Path
            canvas.drawPath(path, paint);

            Log.d(TAG, "Generated preview bitmap for " + points.size() + " points.");
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error generating preview bitmap", e);
            return null;
        }
    }
}