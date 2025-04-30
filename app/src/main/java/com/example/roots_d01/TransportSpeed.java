package com.example.roots_d01;

public class TransportSpeed {
    // Insignificant polyline thresholds
    public static final int WALKING_MIN_POINTS = 10;
    public static final long WALKING_MIN_DURATION = 30000; // 30 seconds
    public static final float WALKING_MIN_DISTANCE = 20; // 20 meters

    public static final int BICYCLING_MIN_POINTS = 15;
    public static final long BICYCLING_MIN_DURATION = 10000; // 10 seconds
    public static final float BICYCLING_MIN_DISTANCE = 30; // 30 meters

    public static final int IN_VEHICLE_MIN_POINTS = 20;
    public static final long IN_VEHICLE_MIN_DURATION = 15000; // 15 seconds
    public static final float IN_VEHICLE_MIN_DISTANCE = 20; // 20 meters
}