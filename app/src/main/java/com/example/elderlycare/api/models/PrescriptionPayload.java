package com.example.elderlycare.api.models;

import java.util.List;

public class PrescriptionPayload {
    private int userId;
    private String extractedText;
    private List<Medication> parsedMedications;
    private long timestamp;

    public PrescriptionPayload(int userId, String extractedText, List<Medication> parsedMedications, long timestamp) {
        this.userId = userId;
        this.extractedText = extractedText;
        this.parsedMedications = parsedMedications;
        this.timestamp = timestamp;
    }

    public static class Medication {
        public String name;
        public String frequency; // e.g. "Morning, Night"

        public Medication(String name, String frequency) {
            this.name = name;
            this.frequency = frequency;
        }
    }

    // Getters and Setters omitted for brevity but accessible publicly
    public int getUserId() { return userId; }
    public String getExtractedText() { return extractedText; }
    public List<Medication> getParsedMedications() { return parsedMedications; }
    public long getTimestamp() { return timestamp; }
}
