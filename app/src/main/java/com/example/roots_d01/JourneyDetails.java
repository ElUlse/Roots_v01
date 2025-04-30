package com.example.roots_d01;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;

public class JourneyDetails implements Parcelable {

    // --- Fields ---
    long startTimeMs;
    long endTimeMs;
    float totalDistanceMeters;
    Map<String, Long> durationPerModeMs;
    List<String> sourceFilenames;
    public List<PolylinePoint> points;
    public String journeyName;
    float averageAccuracy;
    public boolean mapMatched;
    public String matchedShape;



    // --- Constructor (Corrected Signature - 6 arguments) ---
    public JourneyDetails(long start, long end, float dist, Map<String, Long> durations, List<String> filenames, List<PolylinePoint> journeyPoints, String name, float avgAccuracy, boolean mapMatched, String matchedShape) {
        this.startTimeMs = start;
        this.endTimeMs = end;
        this.totalDistanceMeters = dist;
        this.durationPerModeMs = (durations != null) ? durations : new HashMap<>();
        this.sourceFilenames = (filenames != null) ? filenames : new ArrayList<>();
        this.points = (journeyPoints != null) ? journeyPoints : new ArrayList<>();
        // Assign the passed name, use default if null/empty
        this.journeyName = (name != null && !name.isEmpty()) ? name : generateDefaultName(start);
        this.averageAccuracy = avgAccuracy;
        this.mapMatched = mapMatched;
        this.matchedShape = matchedShape;
    }

    // --- Parcelable Implementation (Updated) ---
    protected JourneyDetails(Parcel in) {
        startTimeMs = in.readLong();
        endTimeMs = in.readLong();
        totalDistanceMeters = in.readFloat();
        // Read Map
        int mapSize = in.readInt();
        this.durationPerModeMs = new HashMap<>(mapSize);
        for(int i = 0; i < mapSize; i++){
            String key = in.readString();
            Long value = in.readLong();
            this.durationPerModeMs.put(key, value);
        }
        // Read sourceFilenames List
        this.sourceFilenames = new ArrayList<>();
        in.readStringList(this.sourceFilenames);
        // Read points List
        this.points = new ArrayList<>();
        in.readTypedList(this.points, PolylinePoint.CREATOR); // <<< READ points
        this.journeyName = in.readString(); // <<< READ name
        averageAccuracy = in.readFloat();
        mapMatched = in.readByte() != 0;
        this.matchedShape = in.readString();
    }

    public static final Creator<JourneyDetails> CREATOR = new Creator<JourneyDetails>() {
        @Override
        public JourneyDetails createFromParcel(Parcel in) {
            return new JourneyDetails(in);
        }
        @Override
        public JourneyDetails[] newArray(int size) {
            return new JourneyDetails[size];
        }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(startTimeMs);
        dest.writeLong(endTimeMs);
        dest.writeFloat(totalDistanceMeters);
        // Write Map
        dest.writeInt(durationPerModeMs.size());
        for(Map.Entry<String, Long> entry : durationPerModeMs.entrySet()){
            dest.writeString(entry.getKey());
            dest.writeLong(entry.getValue());
        }
        // Write sourceFilenames List
        dest.writeStringList(sourceFilenames);
        // Write points List
        dest.writeTypedList(this.points); // <<< WRITE points
        dest.writeString(this.journeyName); // <<< WRITE name
        dest.writeFloat(averageAccuracy);
        dest.writeByte((byte) (mapMatched ? 1 : 0));
        dest.writeString(this.matchedShape);
    }
    // --- End Parcelable Implementation ---

    // Helper for default name (called from constructor)
    private String generateDefaultName(long startTime) {
        if (startTime <= 0) return "Journey"; // Handle edge case
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        return "Journey - " + sdf.format(new Date(startTime));
    }

    // --- Formatting Methods ---
    // getFormattedStartTime(), getFormattedEndTime(), getFormattedDuration(),
    // getFormattedDistance(), getDominantMode(), getFormattedDetails()
    // (These methods should remain as they were in the version you pasted previously)
    // Ensure getDominantMode() uses this.points if durationPerModeMs is empty
    // Ensure getFormattedDetails() uses the journeyName field or a getter if you add one.

    public String getFormattedStartTime() {
        if (startTimeMs <= 0) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss (dd MMM)", Locale.getDefault());
        return sdf.format(new Date(startTimeMs));
    }

    public String getFormattedEndTime() {
        if (endTimeMs <= 0) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss (dd MMM)", Locale.getDefault());
        return sdf.format(new Date(endTimeMs));
    }

    public String getFormattedDuration() {
        long durationMs = endTimeMs - startTimeMs;
        if (durationMs < 0) return "N/A";
        long hours = TimeUnit.MILLISECONDS.toHours(durationMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%d hr %d min", hours, minutes);
        else if (minutes > 0) return String.format(Locale.getDefault(), "%d min %d sec", minutes, seconds);
        else return String.format(Locale.getDefault(), "%d sec", seconds);
    }

    public String getFormattedDistance() {
        if (totalDistanceMeters < 1000) return String.format(Locale.getDefault(), "%.0f m", totalDistanceMeters);
        else return String.format(Locale.getDefault(), "%.2f km", totalDistanceMeters / 1000.0f);
    }

    public String getDominantMode() {
        long maxDuration = 0L; String dominant = "Unknown";
        if (durationPerModeMs == null || durationPerModeMs.isEmpty()) {
            if (points != null && !points.isEmpty() && points.get(0).transportMode != null) return points.get(0).transportMode;
            return dominant;
        }
        for (Map.Entry<String, Long> entry : durationPerModeMs.entrySet()) {
            if (entry.getValue() > maxDuration) { maxDuration = entry.getValue(); dominant = entry.getKey(); }
        }
        if (maxDuration == 0 && points != null && !points.isEmpty() && points.get(0).transportMode != null) return points.get(0).transportMode;
        return dominant;
    }

    public String getFormattedDetails() {
        return String.format(Locale.getDefault(),
                "%s\nStart: %s\nEnd: %s\nDuration: %s\nDistance: %s\nMode: %s",
                (journeyName != null ? journeyName : "Journey"),
                getFormattedStartTime(), getFormattedEndTime(), getFormattedDuration(),
                getFormattedDistance(), getDominantMode());
    }

    public float getAverageAccuracy() {
        return averageAccuracy;
    }

    public String getFormattedAverageAccuracy() {
        if (averageAccuracy <= 0) { // Handle invalid or zero accuracy
            return "Accuracy: N/A";
        } else {
            return String.format(Locale.getDefault(), "Accuracy: %.1f m", averageAccuracy);
        }
    }

    public String getMatchedShape() {
        return matchedShape;
    }

    public void setMatchedShape(String matchedShape) {
        this.matchedShape = matchedShape;
    }
}