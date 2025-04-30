package com.example.roots_d01;

import android.os.Parcel;
import android.os.Parcelable;

// Make PolylinePoint implement Parcelable
public class PolylinePoint implements Parcelable { // <-- Implement here
    public double latitude;
    public double longitude;
    public long timestamp;
    public String transportMode;
    public float accuracy;

    // No-arg constructor (keep if needed by other parts, e.g., Gson)
    public PolylinePoint() {}

    public PolylinePoint(double lat, double lon, long time, String mode, float acc) { // Ensure 'float acc' is here
        this.latitude = lat;
        this.longitude = lon;
        this.timestamp = time;
        this.transportMode = (mode != null && !mode.isEmpty()) ? mode : "Unknown";
        this.accuracy = acc; // Store the passed 'acc' parameter
    }


    // --- Parcelable Implementation ---
    protected PolylinePoint(Parcel in) {
        latitude = in.readDouble();
        longitude = in.readDouble();
        timestamp = in.readLong();
        transportMode = in.readString();
        accuracy = in.readFloat(); // <<< READ accuracy
    }

    public static final Creator<PolylinePoint> CREATOR = new Creator<PolylinePoint>() {
        @Override
        public PolylinePoint createFromParcel(Parcel in) {
            return new PolylinePoint(in);
        }

        @Override
        public PolylinePoint[] newArray(int size) {
            return new PolylinePoint[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeLong(timestamp);
        dest.writeString(transportMode);
        dest.writeFloat(accuracy); // <<< WRITE accuracy

    }
    // --- End Parcelable Implementation ---
}