package com.example.elderlycare.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.elderlycare.models.User;
import com.example.elderlycare.models.EmergencyContact;
import com.example.elderlycare.models.LocationLog;
import com.example.elderlycare.models.SpO2Log;
import com.example.elderlycare.models.Alert;
import com.example.elderlycare.models.Medicine;
import com.example.elderlycare.models.HospitalVisit;
import com.example.elderlycare.models.GeofenceSettings;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ElderlyCare.db";
    private static final int DATABASE_VERSION = 13;

    // Table Names
    private static final String TABLE_USERS = "users";
    private static final String TABLE_CONTACTS = "emergency_contacts";
    private static final String TABLE_LOCATION = "location_history";
    private static final String TABLE_SPO2 = "spo2_logs";
    private static final String TABLE_ALERTS = "alerts";
    private static final String TABLE_MEDICINES = "medicines";
    private static final String TABLE_MEDICINE_LOGS = "medicine_logs";
    private static final String TABLE_GEOFENCE = "geofence";
    private static final String TABLE_VISITS = "hospital_visits";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_USERS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "role TEXT, " +
                "name TEXT, " +
                "username TEXT UNIQUE, " +
                "phone TEXT UNIQUE, " +
                "email TEXT UNIQUE, " +
                "passwordHash TEXT, " +
                "guardianUsername TEXT, " +
                "guardianApproved INTEGER DEFAULT 0, " +
                "guardianUsername2 TEXT, " +
                "guardianApproved2 INTEGER DEFAULT 0, " +
                "isMinderConnected INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE " + TABLE_CONTACTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "userId INTEGER, " +
                "name TEXT, " +
                "phone TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_LOCATION + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "userId INTEGER, " +
                "latitude REAL, " +
                "longitude REAL, " +
                "speed REAL, " +
                "timestamp INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_SPO2 + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "userId INTEGER, " +
                "value INTEGER, " +
                "timestamp INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_ALERTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "userId INTEGER, " +
                "type TEXT, " +
                "message TEXT, " +
                "timestamp INTEGER, " +
                "status TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_MEDICINES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "userId INTEGER, " +
                "name TEXT, " +
                "dosage TEXT, " +
                "timing TEXT, " + 
                "status TEXT, " +
                "startDate TEXT, " +
                "endDate TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_MEDICINE_LOGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "medicineId INTEGER, " +
                "timingLabel TEXT, " +
                "scheduledTime INTEGER, " +
                "status TEXT, " +
                "date TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_GEOFENCE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "userId INTEGER, " +
                "latitude REAL, " +
                "longitude REAL, " +
                "radius REAL)");

        db.execSQL("CREATE TABLE " + TABLE_VISITS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "userId INTEGER, " +
                "description TEXT, " +
                "timestamp INTEGER)");
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 13) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEDICINES);
            db.execSQL("CREATE TABLE " + TABLE_MEDICINES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "userId INTEGER, " +
                "name TEXT, " +
                "dosage TEXT, " +
                "timing TEXT, " + 
                "status TEXT, " +
                "startDate TEXT, " +
                "endDate TEXT)");
        }
    }

    public long addUser(User user) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("role", user.getRole());
        values.put("name", user.getName());
        values.put("username", user.getUsername());
        values.put("phone", user.getPhone());
        values.put("email", user.getEmail());
        values.put("passwordHash", user.getPasswordHash());
        values.put("guardianUsername", user.getGuardianUsername());
        values.put("guardianApproved", user.getGuardianApproved());
        values.put("guardianUsername2", user.getGuardianUsername2());
        values.put("guardianApproved2", user.getGuardianApproved2());
        values.put("isMinderConnected", user.getIsMinderConnected());
        return db.insert(TABLE_USERS, null, values);
    }

    public int addOrUpdateUser(User user) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("role", user.getRole());
        values.put("name", user.getName());
        values.put("username", user.getUsername());
        values.put("phone", user.getPhone());
        values.put("email", user.getEmail());
        values.put("guardianUsername", user.getGuardianUsername());
        values.put("guardianApproved", user.getGuardianApproved());
        values.put("guardianUsername2", user.getGuardianUsername2());
        values.put("guardianApproved2", user.getGuardianApproved2());
        values.put("isMinderConnected", user.getIsMinderConnected());

        User existing = getUserByUsername(user.getUsername());
        if (existing == null) {
            existing = getUserByEmail(user.getEmail());
        }

        if (existing != null) {
            db.update(TABLE_USERS, values, "id=?", new String[]{String.valueOf(existing.getId())});
            return existing.getId();
        } else {
            return (int) db.insert(TABLE_USERS, null, values);
        }
    }

    public User getUserByUsername(String username) {
        if (username == null) return null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, "username=?", new String[]{username}, null, null, null);
        User user = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                user = cursorToUser(cursor);
            }
            cursor.close();
        }
        return user;
    }

    public User getUserByPhone(String phone) {
        if (phone == null) return null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, "phone=?", new String[]{phone}, null, null, null);
        User user = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                user = cursorToUser(cursor);
            }
            cursor.close();
        }
        return user;
    }

    public User getUserByEmail(String email) {
        if (email == null) return null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, "email=?", new String[]{email}, null, null, null);
        User user = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                user = cursorToUser(cursor);
            }
            cursor.close();
        }
        return user;
    }

    public User getUserById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        User user = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                user = cursorToUser(cursor);
            }
            cursor.close();
        }
        return user;
    }

    public Medicine getMedicineById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MEDICINES, null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        Medicine med = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                med = cursorToMedicine(cursor);
            }
            cursor.close();
        }
        return med;
    }

    private Medicine cursorToMedicine(Cursor cursor) {
        Medicine med = new Medicine();
        med.setId(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
        med.setUserId(cursor.getInt(cursor.getColumnIndexOrThrow("userId")));
        med.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        med.setDosage(cursor.getString(cursor.getColumnIndexOrThrow("dosage")));
        med.setTiming(cursor.getString(cursor.getColumnIndexOrThrow("timing")));
        med.setStatus(cursor.getString(cursor.getColumnIndexOrThrow("status")));
        med.setStartDate(cursor.getString(cursor.getColumnIndexOrThrow("startDate")));
        med.setEndDate(cursor.getString(cursor.getColumnIndexOrThrow("endDate")));
        return med;
    }

    private User cursorToUser(Cursor cursor) {
        try {
            int idIndex = cursor.getColumnIndexOrThrow("id");
            int roleIndex = cursor.getColumnIndexOrThrow("role");
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            int userIndex = cursor.getColumnIndexOrThrow("username");
            int phoneIndex = cursor.getColumnIndexOrThrow("phone");
            int emailIndex = cursor.getColumnIndexOrThrow("email");
            int passIndex = cursor.getColumnIndexOrThrow("passwordHash");
            int guardIndex = cursor.getColumnIndexOrThrow("guardianUsername");
            int appIndex = cursor.getColumnIndexOrThrow("guardianApproved");
            int guardIndex2 = cursor.getColumnIndexOrThrow("guardianUsername2");
            int appIndex2 = cursor.getColumnIndexOrThrow("guardianApproved2");
            int minderIndex = cursor.getColumnIndexOrThrow("isMinderConnected"); 

            User u = new User(
                    cursor.getInt(idIndex),
                    cursor.getString(roleIndex),
                    cursor.getString(nameIndex),
                    cursor.getString(userIndex),
                    cursor.getString(phoneIndex),
                    cursor.getString(emailIndex),
                    cursor.getString(passIndex),
                    cursor.getString(guardIndex),
                    cursor.getInt(appIndex),
                    cursor.getInt(minderIndex)
            );
            u.setGuardianUsername2(cursor.getString(guardIndex2));
            u.setGuardianApproved2(cursor.getInt(appIndex2));
            return u;
        } catch (Exception e) {
            return null;
        }
    }
    
    public int updateMinderConnectionStatus(int userId, int status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("isMinderConnected", status);
        return db.update(TABLE_USERS, values, "id=?", new String[]{String.valueOf(userId)});
    }
    
    public int updateGuardianApproval(int elderlyId, int status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("guardianApproved", status);
        return db.update(TABLE_USERS, values, "id=?", new String[]{String.valueOf(elderlyId)});
    }

    public int updateGuardianApproval2(int elderlyId, int status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("guardianApproved2", status);
        return db.update(TABLE_USERS, values, "id=?", new String[]{String.valueOf(elderlyId)});
    }
    
    public List<User> getElderlyForGuardian(String guardianUsername) {
        List<User> list = new ArrayList<>();
        if (guardianUsername == null) return list;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, "(guardianUsername=? OR guardianUsername2=?) AND role=?", new String[]{guardianUsername, guardianUsername, "Elderly"}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    User u = cursorToUser(cursor);
                    if (u != null) list.add(u);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return list;
    }

    public long insertAlert(Alert alert) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("userId", alert.getUserId());
        values.put("type", alert.getType());
        values.put("message", alert.getMessage());
        values.put("timestamp", alert.getTimestamp());
        values.put("status", alert.getStatus());
        return db.insert(TABLE_ALERTS, null, values);
    }
    
    public Alert getUnresolvedAlertForUser(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_ALERTS, null, "userId=? AND status=?", new String[]{String.valueOf(userId), "NEW"}, null, null, "timestamp DESC", "1");
        Alert alert = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                alert = new Alert(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("userId")),
                        cursor.getString(cursor.getColumnIndexOrThrow("type")),
                        cursor.getString(cursor.getColumnIndexOrThrow("message")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        cursor.getString(cursor.getColumnIndexOrThrow("status"))
                );
            }
            cursor.close();
        }
        return alert;
    }
    
    public int updateAlertStatus(int alertId, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("status", status);
        return db.update(TABLE_ALERTS, values, "id=?", new String[]{String.valueOf(alertId)});
    }

    public long insertLocation(LocationLog log) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("userId", log.getUserId());
        values.put("latitude", log.getLatitude());
        values.put("longitude", log.getLongitude());
        values.put("speed", log.getSpeed());
        values.put("timestamp", log.getTimestamp());
        return db.insert(TABLE_LOCATION, null, values);
    }
    
    public List<Medicine> getMedicinesForUser(int userId) {
        List<Medicine> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MEDICINES, null, "userId=?", new String[]{String.valueOf(userId)}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursorToMedicine(cursor));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return list;
    }
    
    public int updateMedicineStatus(int medicineId, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("status", status);
        return db.update(TABLE_MEDICINES, values, "id=?", new String[]{String.valueOf(medicineId)});
    }

    public int deleteMedicine(int medicineId) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_MEDICINES, "id=?", new String[]{String.valueOf(medicineId)});
    }

    public List<ContentValues> getMedicineLogsForToday(String date) {
        List<ContentValues> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MEDICINE_LOGS, null, "date=?", new String[]{date}, null, null, "scheduledTime ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                android.database.DatabaseUtils.cursorRowToContentValues(cursor, values);
                list.add(values);
            }
            cursor.close();
        }
        return list;
    }

    public int updateMedicineLogStatus(int logId, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("status", status);
        return db.update(TABLE_MEDICINE_LOGS, values, "id=?", new String[]{String.valueOf(logId)});
    }
    
    public LocationLog getLatestLocationForUser(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_LOCATION, null, "userId=?", new String[]{String.valueOf(userId)}, null, null, "timestamp DESC", "1");
        LocationLog log = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                log = new LocationLog(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("userId")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                        cursor.getFloat(cursor.getColumnIndexOrThrow("speed")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                );
            }
            cursor.close();
        }
        return log;
    }
    
    public long insertSpO2(SpO2Log log) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("userId", log.getUserId());
        values.put("value", log.getValue());
        values.put("timestamp", log.getTimestamp());
        return db.insert(TABLE_SPO2, null, values);
    }

    public long addGeofence(int userId, double latitude, double longitude, float radius) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_GEOFENCE, "userId=?", new String[]{String.valueOf(userId)});
        
        ContentValues values = new ContentValues();
        values.put("userId", userId);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("radius", radius);
        return db.insert(TABLE_GEOFENCE, null, values);
    }

    public List<GeofenceSettings> getGeofencesForUser(int userId) {
        List<GeofenceSettings> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_GEOFENCE, null, "userId=?", new String[]{String.valueOf(userId)}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    list.add(new GeofenceSettings(
                            cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                            userId,
                            cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                            cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                            cursor.getFloat(cursor.getColumnIndexOrThrow("radius"))
                    ));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return list;
    }

    public long insertHospitalVisit(HospitalVisit visit) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("userId", visit.getUserId());
        values.put("description", visit.getDescription());
        values.put("timestamp", visit.getTimestamp());
        return db.insert(TABLE_VISITS, null, values);
    }

    public List<HospitalVisit> getHospitalVisitsForUser(int userId) {
        List<HospitalVisit> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_VISITS, null, "userId=?", new String[]{String.valueOf(userId)}, null, null, "timestamp DESC");
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    list.add(new HospitalVisit(
                            cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("userId")),
                            cursor.getString(cursor.getColumnIndexOrThrow("description")),
                            cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                    ));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return list;
    }
}
