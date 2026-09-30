package com.example.elderlycare.models;

public class EmergencyContact {
    private int id;
    private int userId;
    private String name;
    private String phone;

    public EmergencyContact() {}

    public EmergencyContact(int id, int userId, String name, String phone) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.phone = phone;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
