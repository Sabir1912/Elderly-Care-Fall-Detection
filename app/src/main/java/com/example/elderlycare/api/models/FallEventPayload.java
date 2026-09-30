package com.example.elderlycare.api.models;

public class FallEventPayload {
    private int userId;
    private boolean isFallDetected;
    private double accelX;
    private double accelY;
    private double accelZ;
    private long timestamp;

    public FallEventPayload(int userId, boolean isFallDetected, double accelX, double accelY, double accelZ, long timestamp) {
        this.userId = userId;
        this.isFallDetected = isFallDetected;
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
        this.timestamp = timestamp;
    }

    public int getUserId() { return userId; }
    public boolean isFallDetected() { return isFallDetected; }
    public double getAccelX() { return accelX; }
    public double getAccelY() { return accelY; }
    public double getAccelZ() { return accelZ; }
    public long getTimestamp() { return timestamp; }
}
