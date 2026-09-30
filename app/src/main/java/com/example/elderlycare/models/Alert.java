package com.example.elderlycare.models;

public class Alert {
    private int id;
    private int userId;
    private String type; // "FALL", "SOS", "GEOFENCE", "SPEED", "SPO2"
    private String message;
    private long timestamp;
    private String status; // "NEW", "ACKNOWLEDGED"

    public Alert() {}

    public Alert(int id, int userId, String type, String message, long timestamp, String status) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.timestamp = timestamp;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
