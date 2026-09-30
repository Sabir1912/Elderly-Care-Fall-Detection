package com.example.elderlycare.api.models;

public class LocationPayload {
    private int userId;
    private double latitude;
    private double longitude;
    private float speed;
    private long timestamp;

    public LocationPayload(int userId, double latitude, double longitude, float speed, long timestamp) {
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.timestamp = timestamp;
    }

    public int getUserId() { return userId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public float getSpeed() { return speed; }
    public long getTimestamp() { return timestamp; }
}
