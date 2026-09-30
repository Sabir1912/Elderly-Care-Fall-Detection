package com.example.elderlycare.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "ElderlyCareSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_FIREBASE_UID = "firebaseUid";
    private static final String KEY_ROLE = "role";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_LAST_ESP_IP = "lastEspIp";

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void createLoginSession(int userId, String role, String firebaseUid, String username, String phone) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_ROLE, role);
        editor.putString(KEY_FIREBASE_UID, firebaseUid);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_PHONE, phone);
        editor.apply();
    }
    
    public void createCloudSession(String role, String firebaseUid, String username, String phone) {
        createLoginSession(-1, role, firebaseUid, username, phone);
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public int getUserId() {
        return pref.getInt(KEY_USER_ID, -1);
    }

    public String getUsername() {
        return pref.getString(KEY_USERNAME, null);
    }

    public String getPhone() {
        return pref.getString(KEY_PHONE, null);
    }

    public String getRole() {
        return pref.getString(KEY_ROLE, null);
    }

    public String getFirebaseUid() {
        return pref.getString(KEY_FIREBASE_UID, null);
    }

    public void saveLastEspIp(String ip) {
        editor.putString(KEY_LAST_ESP_IP, ip);
        editor.apply();
    }

    public String getLastEspIp() {
        return pref.getString(KEY_LAST_ESP_IP, "");
    }

    public void logout() {
        editor.clear();
        editor.apply();
    }
}
