package com.example.elderlycare.api.models;

public class SpO2Payload {
    private int userId;
    private int spo2Level;
    private int heartRate;
    private long timestamp;

    public SpO2Payload(int userId, int spo2Level, int heartRate, long timestamp) {
        this.userId = userId;
        this.spo2Level = spo2Level;
        this.heartRate = heartRate;
        this.timestamp = timestamp;
    }

    public int getUserId() { return userId; }
    public int getSpo2Level() { return spo2Level; }
    public int getHeartRate() { return heartRate; }
    public long getTimestamp() { return timestamp; }
}
