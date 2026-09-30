package com.example.elderlycare.models;

public class User {
    private int id;
    private String role; // "Elderly" or "Guardian"
    private String name;
    private String username; 
    private String phone;    
    private String email;
    private String passwordHash;
    private String guardianUsername;
    private int guardianApproved; // 0 for pending, 1 for approved
    private String guardianUsername2;
    private int guardianApproved2; // 0 for pending, 1 for approved
    private int isMinderConnected; // 0 for disconnected, 1 for connected

    public User() {}

    public User(int id, String role, String name, String username, String phone, String email, String passwordHash, String guardianUsername, int guardianApproved, int isMinderConnected) {
        this.id = id;
        this.role = role;
        this.name = name;
        this.username = username;
        this.phone = phone;
        this.email = email;
        this.passwordHash = passwordHash;
        this.guardianUsername = guardianUsername;
        this.guardianApproved = guardianApproved;
        this.isMinderConnected = isMinderConnected;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getGuardianUsername() { return guardianUsername; }
    public void setGuardianUsername(String guardianUsername) { this.guardianUsername = guardianUsername; }
    public int getGuardianApproved() { return guardianApproved; }
    public void setGuardianApproved(int guardianApproved) { this.guardianApproved = guardianApproved; }
    public String getGuardianUsername2() { return guardianUsername2; }
    public void setGuardianUsername2(String guardianUsername2) { this.guardianUsername2 = guardianUsername2; }
    public int getGuardianApproved2() { return guardianApproved2; }
    public void setGuardianApproved2(int guardianApproved2) { this.guardianApproved2 = guardianApproved2; }
    public int getIsMinderConnected() { return isMinderConnected; }
    public void setIsMinderConnected(int isMinderConnected) { this.isMinderConnected = isMinderConnected; }
}
