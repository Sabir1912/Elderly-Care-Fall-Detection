package com.example.elderlycare.api.models;

public class DashboardAnalytics {
    private int takenMedicines;
    private int totalMedicines;
    private String lastLocationStatus;
    private String lastActive;

    public int getTakenMedicines() { return takenMedicines; }
    public int getTotalMedicines() { return totalMedicines; }
    public String getLastLocationStatus() { return lastLocationStatus; }
    public String getLastActive() { return lastActive; }

    public float getMedicineCompliancePercent() {
        if (totalMedicines == 0) return 0;
        return ((float) takenMedicines / totalMedicines) * 100;
    }
}
