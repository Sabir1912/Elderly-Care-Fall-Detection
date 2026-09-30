package com.example.elderlycare.models;

public class GeofenceSettings {
    private int id;
    private int userId;
    private double latitude;
    private double longitude;
    private float radius; // in meters

    public GeofenceSettings() {}

    public GeofenceSettings(int id, int userId, double latitude, double longitude, float radius) {
        this.id = id;
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public float getRadius() { return radius; }
    public void setRadius(float radius) { this.radius = radius; }
}
