package com.example.roots_d01;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.util.Log; // Optional: For logging within ViewModel

public class MainViewModel extends ViewModel {

    private static final String TAG = "MainViewModel"; // Optional logging tag

    // State variables managed by ViewModel using LiveData
    // Initialize with default values
    private final MutableLiveData<Boolean> _isTrackingActive = new MutableLiveData<>(false);
    private final MutableLiveData<String> _overrideMode = new MutableLiveData<>(null);
    // Add LiveData for other state if needed, e.g., current displayed mode string
    private final MutableLiveData<String> _displayMode = new MutableLiveData<>("Unknown");
    private final MutableLiveData<String> _statusLabelText = new MutableLiveData<>(null);


    // Public immutable LiveData exposed to the Activity/Fragment
    public LiveData<Boolean> isTrackingActive() {
        return _isTrackingActive;
    }

    public LiveData<String> overrideMode() {
        return _overrideMode;
    }

    public LiveData<String> displayMode() {
        return _displayMode;
    }

    public LiveData<String> statusLabelText() {
        return _statusLabelText;
    }


    // --- Methods to update state ---

    public void setIsTrackingActive(boolean tracking) {
        if (_isTrackingActive.getValue() == null || _isTrackingActive.getValue() != tracking) {
            Log.d(TAG, "Setting isTrackingActive: " + tracking); // Optional log
            _isTrackingActive.setValue(tracking);
            // Reset override mode when tracking stops? Or handled elsewhere?
            // if (!tracking) {
            //     clearOverrideMode();
            // }
        }
    }

    public void setOverrideMode(String mode) {
        if (_overrideMode.getValue() == null || !_overrideMode.getValue().equals(mode)) {
            Log.d(TAG, "Setting overrideMode: " + mode); // Optional log
            _overrideMode.setValue(mode);
            // If setting an override, also update the display mode immediately
            if (mode != null) {
                _displayMode.setValue(mode);
                updateStatusLabel(); // Update label based on new override
            }
        }
    }

    public void clearOverrideMode() {
        if (_overrideMode.getValue() != null) {
            Log.d(TAG, "Clearing overrideMode"); // Optional log
            _overrideMode.setValue(null);
            // When override is cleared, display mode should probably revert
            // This might need input from the latest detected activity
            // For now, maybe set to Unknown or let the next update handle it.
            // _displayMode.setValue("Unknown"); // Or another appropriate default
            // updateStatusLabel(); // Update label
        }
    }

    // Method to update the general display mode (called from service updates)
    public void updateDisplayMode(String mode) {
        // Only update if override isn't active
        if (_overrideMode.getValue() == null) {
            if (_displayMode.getValue() == null || !_displayMode.getValue().equals(mode)) {
                Log.d(TAG, "Setting displayMode: " + mode);
                _displayMode.setValue(mode);
                updateStatusLabel(); // Update label based on new display mode
            }
        } else {
            Log.d(TAG, "DisplayMode update skipped, override active: " + _overrideMode.getValue());
            // Ensure display mode still reflects override if it changed somehow
            if(_displayMode.getValue() == null || !_displayMode.getValue().equals(_overrideMode.getValue())) {
                _displayMode.setValue(_overrideMode.getValue());
                updateStatusLabel();
            }
        }
    }

    // Method to update status label based on tracking state and mode
    public void updateStatusLabel() {
        boolean tracking = _isTrackingActive.getValue() != null && _isTrackingActive.getValue();
        String currentDisplay = _displayMode.getValue();
        String currentOverride = _overrideMode.getValue();
        String label;

        if (tracking) {
            if (currentOverride != null) {
                label = "Tracking: " + currentOverride;
            } else if (currentDisplay != null && !currentDisplay.equals("Unknown") && !currentDisplay.equals("Still")) {
                label = "Tracking: " + currentDisplay;
            } else {
                label = "Tracking..."; // Generic tracking state
            }
        } else {
            // Handle inactive states - get tracking mode preference maybe?
            // This part depends on how you want inactive UI to look
            // For now, just clear it. updateUiBasedOnTrackingMode in Activity might handle this better.
            label = null; // Or "Idle", "Ready", etc.
        }

        if(_statusLabelText.getValue() == null || !_statusLabelText.getValue().equals(label)) {
            _statusLabelText.setValue(label);
            Log.d(TAG, "Setting statusLabelText: " + label);
        }
    }

    // You can add more methods here if the ViewModel needs to perform other logic
    // related to the state (e.g., formatting data for the UI).
}