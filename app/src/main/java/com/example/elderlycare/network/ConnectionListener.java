package com.example.elderlycare.network;

/**
 * Interface to receive events from the ESP32 SafeNode device.
 */
public interface ConnectionListener {
    void onFallDetected(float confidence);
    void onSosReceived();
    void onStatusChanged(String state);        // LOW_POWER / ACTIVE / ALERT
    void onHeartbeat();
    void onWifiStateChanged(boolean connected);
    void onBluetoothStateChanged(boolean connected);
    void onMessageLog(String message);
}
