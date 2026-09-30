package com.example.elderlycare.models;

public class HospitalVisit {
    private int id;
    private int userId;
    private String description;
    private long timestamp;

    public HospitalVisit() {}

    public HospitalVisit(int id, int userId, String description, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.description = description;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
