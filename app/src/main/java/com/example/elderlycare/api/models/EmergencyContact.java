package com.example.elderlycare.api.models;

public class EmergencyContact {
    private int id;
    private int userId;
    private String name;
    private String phone;

    public EmergencyContact(int id, int userId, String name, String phone) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.phone = phone;
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
}
