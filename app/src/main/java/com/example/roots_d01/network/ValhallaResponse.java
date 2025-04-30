package com.example.roots_d01.network;

import com.google.gson.annotations.SerializedName; // Needed if JSON keys differ from Java field names
import java.util.List;

// Represents the top-level response from Valhalla trace_attributes
public class ValhallaResponse {

    @SerializedName("shape") // Encoded polyline string (polyline6 format)
    String shape;

    @SerializedName("matched_points")
    List<MatchedPoint> matchedPoints;

    @SerializedName("edges")
    List<Edge> edges;

    @SerializedName("admins") // Corrected based on typical Valhalla structure
    List<Admin> admins;

    // --- Getters (Optional but good practice) ---
    public String getShape() { return shape; }
    public List<MatchedPoint> getMatchedPoints() { return matchedPoints; }
    public List<Edge> getEdges() { return edges; }
    public List<Admin> getAdmins() { return admins; }


    // --- Inner class for matched points ---
    public static class MatchedPoint {
        @SerializedName("lat")
        double lat;

        @SerializedName("lon")
        double lon;

        @SerializedName("type") // 0: unmatched, 1: interpolated, 2: matched
        String type;

        @SerializedName("edge_index")
        String edgeIndex; // Changed from long to String

        @SerializedName("distance_along_edge")
        double distanceAlongEdge;

        @SerializedName("distance_from_trace_point")
        double distanceFromTracePoint;

        // --- Getters ---
        public double getLat() { return lat; }
        public double getLon() { return lon; }
        public String getType() { return type; } // Getter should return String
        // ... getters for other fields ...
    }

    // --- Inner class for matched edges (roads) ---
    public static class Edge {
        // Basic example - Add fields based on what you need from Valhalla docs
        // Common fields: way_id, names, length, speed, road_class, begin_shape_index, end_shape_index etc.
        @SerializedName("way_id")
        long wayId;

        @SerializedName("speed")
        double speed; // Speed limit or typical speed

        @SerializedName("length")
        double length; // Length of this edge segment in km

        @SerializedName("begin_shape_index")
        int beginShapeIndex;

        @SerializedName("end_shape_index")
        int endShapeIndex;

        @SerializedName("names")
        List<String> names;

        // Add other fields as needed...

        // --- Getters ---
        public long getWayId() { return wayId; }
        public double getSpeed() { return speed; }
        public double getLength() { return length; }
        public List<String> getNames() { return names;}
        // ... getters for other fields ...
    }

    // --- Inner class for administrative regions ---
    public static class Admin {
        @SerializedName("iso_3166_1")
        String countryCode; // e.g., "GB"

        @SerializedName("state")
        String state;       // e.g., "England"

        // Add other admin fields if needed (e.g., county)

        // --- Getters ---
        public String getCountryCode() { return countryCode; }
        public String getState() { return state; }

    }
}