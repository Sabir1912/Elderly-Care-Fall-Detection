package com.example.elderlycare.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.example.elderlycare.R;
import com.example.elderlycare.activities.HospitalVisitActivity;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.LocationLog;
import com.example.elderlycare.utils.SessionManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import com.example.elderlycare.api.ApiClient;
import com.example.elderlycare.api.ApiService;
import com.example.elderlycare.api.models.LocationPayload;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LocationTrackingService extends Service {

    private static final String TAG = "LocationTrackingService";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private FirebaseFirestore firestore;
    private int userId;
    private String userUid;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        dbHelper = new DatabaseHelper(this);
        sessionManager = new SessionManager(this);
        firestore = FirebaseFirestore.getInstance();
        userId = sessionManager.getUserId();
        userUid = sessionManager.getFirebaseUid();
        
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ElderlyCare:LocationWakeLock");
            wakeLock.acquire(10*60*1000L /*10 minutes fallback*/);
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    Log.d(TAG, "Live Location Update: " + location.getLatitude() + ", " + location.getLongitude() + " Accuracy: " + location.getAccuracy());
                    float speed = location.hasSpeed() ? location.getSpeed() : 0.0f;
                    
                    // 1. Local Persistence
                    LocationLog log = new LocationLog(0, userId, location.getLatitude(), location.getLongitude(), speed, System.currentTimeMillis());
                    dbHelper.insertLocation(log);
                    
                    // 2. Real-time Firebase Sync (Primary for Guardian Live View)
                    if (userUid != null) {
                        Map<String, Object> locData = new HashMap<>();
                        locData.put("latitude", location.getLatitude());
                        locData.put("longitude", location.getLongitude());
                        locData.put("speed", speed);
                        locData.put("accuracy", location.getAccuracy());
                        locData.put("timestamp", System.currentTimeMillis());
                        
                        firestore.collection("users").document(userUid)
                                .collection("live_location").document("current")
                                .set(locData)
                                .addOnFailureListener(e -> Log.e(TAG, "Firestore update failed", e));
                    }
                    
                    // 3. Backend Streaming
                    streamToBackend(location, speed);
                    
                    checkGeofenceAndSpeed(location, speed);
                }
            }
        };
    }
    
    private void streamToBackend(Location location, float speed) {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        LocationPayload payload = new LocationPayload(userId, location.getLatitude(), location.getLongitude(), speed, System.currentTimeMillis());
        String token = "Bearer sample_token"; 
        
        apiService.sendLiveLocation(token, payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) Log.d(TAG, "Streamed to Kafka.");
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Stream failed", t);
            }
        });
    }

    private void checkGeofenceAndSpeed(Location location, float speed) {
        java.util.List<com.example.elderlycare.models.GeofenceSettings> geofences = dbHelper.getGeofencesForUser(userId);
        for(com.example.elderlycare.models.GeofenceSettings g : geofences) {
            Location center = new Location("");
            center.setLatitude(g.getLatitude());
            center.setLongitude(g.getLongitude());
            if (location.distanceTo(center) > g.getRadius()) {
                triggerGeofenceNotification();
                
                com.example.elderlycare.models.Alert alert = new com.example.elderlycare.models.Alert(
                        0, userId, "Geofence Breach", "User has left the safe zone.", System.currentTimeMillis(), "NEW");
                dbHelper.insertAlert(alert);
            }
        }
        checkHospitalProximityAsync(location);
    }
    
    private long lastHospitalCheckTime = 0;
    private static final String ORS_API_KEY = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImI0NjRiZDg5MzMzZDQ1NGFiN2Q1MjM0MmVkM2RlMjJlIiwiaCI6Im11cm11cjY0In0";
    
    private void checkHospitalProximityAsync(Location userLocation) {
        if (System.currentTimeMillis() - lastHospitalCheckTime < 300000) return; 
        lastHospitalCheckTime = System.currentTimeMillis();
        
        new Thread(() -> {
            try {
                String urlStr = "https://api.openrouteservice.org/geocode/search?" + 
                        "api_key=" + ORS_API_KEY + 
                        "&text=hospital" + 
                        "&focus.point.lat=" + userLocation.getLatitude() + 
                        "&focus.point.lon=" + userLocation.getLongitude() + 
                        "&boundary.circle.lat=" + userLocation.getLatitude() + 
                        "&boundary.circle.lon=" + userLocation.getLongitude() + 
                        "&boundary.circle.radius=0.2"; 
                
                java.net.URL url = new java.net.URL(urlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while((line = in.readLine()) != null) response.append(line);
                    in.close();
                    
                    org.json.JSONObject json = new org.json.JSONObject(response.toString());
                    org.json.JSONArray features = json.optJSONArray("features");
                    if (features != null && features.length() > 0) {
                        new android.os.Handler(Looper.getMainLooper()).post(() -> triggerHospitalNotification());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "ORS API Error: " + e.getMessage());
            }
        }).start();
    }
    
    private long lastGeofenceNotifTime = 0;
    private void triggerGeofenceNotification() {
        if (System.currentTimeMillis() - lastGeofenceNotifTime < 300000) return; 
        lastGeofenceNotifTime = System.currentTimeMillis();
        
        Notification notification = new NotificationCompat.Builder(this, "LocationChannel")
                .setContentTitle("Safe Zone Exited!")
                .setContentText("User has moved outside the designated safe boundary.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
                
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(3, notification);
    }
    
    private long lastHospitalNotifTime = 0;
    private void triggerHospitalNotification() {
        if (System.currentTimeMillis() - lastHospitalNotifTime < 600000) return; 
        lastHospitalNotifTime = System.currentTimeMillis();
        
        Intent intent = new Intent(this, HospitalVisitActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        
        Notification notification = new NotificationCompat.Builder(this, "LocationChannel")
                .setContentTitle("Hospital Nearby Detected")
                .setContentText("Tap to log your visit and add prescriptions.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();
                
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(2, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, "LocationChannel")
                .setContentTitle("Elderly Care Safety Active")
                .setContentText("Tracking live location for your guardians...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(1, notification);
        }
        startLocationUpdates();
        return START_STICKY;
    }

    private void startLocationUpdates() {
        // Higher frequency and high accuracy for live tracking
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(2000)
                .setMaxUpdateDelayMillis(1000)
                .setWaitForAccurateLocation(true)
                .build();
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "LocationChannel",
                    "Safety Tracking Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
