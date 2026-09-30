package com.example.elderlycare.models;

public class SpO2Log {
    private int id;
    private int userId;
    private int value;
    private long timestamp;

    public SpO2Log() {}

    public SpO2Log(int id, int userId, int value, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.value = value;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
