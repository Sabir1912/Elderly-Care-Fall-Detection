package com.example.elderlycare.models;

public class Medicine {
    private int id;
    private int userId;
    private String name;
    private String dosage;
    private String timing; // e.g. "08:00,13:00,18:00"
    private String status; // e.g. "Pending,Taken,Pending"
    private String startDate;
    private String endDate;
    private String notes;

    public Medicine() {}

    public Medicine(int id, int userId, String name, String dosage, String timing, String status) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.dosage = dosage;
        this.timing = timing;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public String getTiming() { return timing; }
    public void setTiming(String timing) { this.timing = timing; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
