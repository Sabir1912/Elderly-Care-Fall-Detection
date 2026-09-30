package com.example.elderlycare.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.elderlycare.R;
import com.example.elderlycare.activities.ElderlyLiveDeviceDataActivity;
import com.example.elderlycare.activities.FallAlertActivity;
import com.example.elderlycare.bluetooth.BluetoothService;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.Alert;
import com.example.elderlycare.models.SpO2Log;
import com.example.elderlycare.utils.SessionManager;

import com.example.elderlycare.api.ApiClient;
import com.example.elderlycare.api.ApiService;
import com.example.elderlycare.api.models.FallEventPayload;
import com.example.elderlycare.api.models.SpO2Payload;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BluetoothListenerService extends Service {

    private static final String TAG = "BTListenerService";
    private BluetoothService bluetoothService;
    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private FirebaseFirestore firestore;
    private int userId;
    private String userUid;

    private Handler watchdogHandler;
    private Runnable watchdogRunnable;
    private static final long WATCHDOG_TIMEOUT_MS = 5000; 
    private boolean isMinderConnected = false;

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new DatabaseHelper(this);
        sessionManager = new SessionManager(this);
        firestore = FirebaseFirestore.getInstance();
        userId = sessionManager.getUserId();
        userUid = sessionManager.getFirebaseUid();
        
        watchdogHandler = new Handler(Looper.getMainLooper());
        watchdogRunnable = () -> {
            if (isMinderConnected) {
                Log.w(TAG, "Watchdog timeout! No data received from Minder.");
                handleMinderDisconnection();
            }
        };
        
        bluetoothService = new BluetoothService();
        
        android.content.BroadcastReceiver outgoingReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                String data = intent.getStringExtra("data");
                if (data != null && bluetoothService != null) {
                    bluetoothService.write(data.getBytes());
                }
            }
        };
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .registerReceiver(outgoingReceiver, new android.content.IntentFilter("com.example.elderlycare.ACTION_SEND_BT_DATA"));

        bluetoothService.setListener(new BluetoothService.BluetoothListener() {
            @Override
            public void onMessageReceived(String message) {
                handleIncomingMessage(message.trim());
            }

            @Override
            public void onConnectionStateChanged(int state) {
                Log.d(TAG, "BT State: " + state);
                if (state == BluetoothService.STATE_CONNECTED) {
                    isMinderConnected = true;
                    dbHelper.updateMinderConnectionStatus(userId, 1);
                    updateMinderStatusInCloud(1);
                    resetWatchdog();
                } else if (state == BluetoothService.STATE_NONE) {
                    if (isMinderConnected) {
                        handleMinderDisconnection();
                    }
                }
            }
        });
    }

    private void resetWatchdog() {
        watchdogHandler.removeCallbacks(watchdogRunnable);
        watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS);
    }
    
    private void handleMinderDisconnection() {
        isMinderConnected = false;
        watchdogHandler.removeCallbacks(watchdogRunnable);
        
        dbHelper.updateMinderConnectionStatus(userId, 0);
        updateMinderStatusInCloud(0);
        
        if (userUid != null) {
            Alert alert = new Alert(0, userId, "DISCONNECTED", "Minder device disconnected!", System.currentTimeMillis(), "NEW");
            dbHelper.insertAlert(alert);
            
            Map<String, Object> alertData = new HashMap<>();
            alertData.put("type", "DISCONNECTED");
            alertData.put("message", "The elderly's device has lost connection.");
            alertData.put("timestamp", System.currentTimeMillis());
            alertData.put("status", "NEW");
            
            firestore.collection("users").document(userUid)
                    .collection("alerts").add(alertData);
        }

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "BTChannel")
                    .setContentTitle("Alert: Minder Disconnected")
                    .setContentText("Minder is not connected. Please check your device.")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVibrate(new long[]{0, 500, 500})
                    .setAutoCancel(true);
            manager.notify(3, builder.build());
        }
    }

    private void updateMinderStatusInCloud(int status) {
        if (userUid != null) {
            firestore.collection("users").document(userUid)
                    .update("isMinderConnected", status);
        }
    }

    private void handleIncomingMessage(String msg) {
        if (!isMinderConnected) return;
        resetWatchdog();
        
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        String token = "Bearer sample_token"; 

        Intent broadcastIntent = new Intent(ElderlyLiveDeviceDataActivity.ACTION_LIVE_DATA);
        boolean sendBroadcast = false;

        if (msg.startsWith("ACC:")) {
            try {
                String accData = msg.substring(4); 
                broadcastIntent.putExtra(ElderlyLiveDeviceDataActivity.EXTRA_ACC_DATA, accData);
                sendBroadcast = true;
            } catch (Exception e) {}
        } else if (msg.startsWith("GYRO:")) {
            try {
                String gyroData = msg.substring(5);
                broadcastIntent.putExtra(ElderlyLiveDeviceDataActivity.EXTRA_GYRO_DATA, gyroData);
                sendBroadcast = true;
            } catch (Exception e) {}
        } else if (msg.startsWith("BPM:")) {
            try {
                String bpmData = msg.substring(4);
                broadcastIntent.putExtra(ElderlyLiveDeviceDataActivity.EXTRA_BPM_DATA, bpmData);
                sendBroadcast = true;
            } catch (Exception e) {}
        } else if (msg.startsWith("LOC:")) {
            try {
                String[] parts = msg.substring(4).split(",");
                double lat = Double.parseDouble(parts[0]);
                double lon = Double.parseDouble(parts[1]);
                
                com.example.elderlycare.models.LocationLog log = 
                    new com.example.elderlycare.models.LocationLog(0, userId, lat, lon, 0, System.currentTimeMillis());
                dbHelper.insertLocation(log);
                
                if (userUid != null) {
                    Map<String, Object> loc = new HashMap<>();
                    loc.put("latitude", lat);
                    loc.put("longitude", lon);
                    loc.put("timestamp", System.currentTimeMillis());
                    firestore.collection("users").document(userUid)
                            .collection("live_location").document("current").set(loc);
                }

                com.example.elderlycare.api.models.LocationPayload payload = 
                    new com.example.elderlycare.api.models.LocationPayload(userId, lat, lon, 0, System.currentTimeMillis());
                apiService.sendLiveLocation(token, payload).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) { }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) { }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error parsing LOC", e);
            }
        } else if (msg.startsWith("FALL:")) {
            Log.d(TAG, "FALL ALERT received from ESP32");
            
            // 1. Show local FallAlertActivity immediately on Elderly phone
            Intent fallIntent = new Intent(this, FallAlertActivity.class);
            fallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(fallIntent);

            // 2. Save alert locally
            Alert alert = new Alert(0, userId, "FALL", "Fall detected by Minder device!", System.currentTimeMillis(), "NEW");
            dbHelper.insertAlert(alert);
            
            // 3. Send to Cloud immediately for Guardian
            if (userUid != null) {
                Map<String, Object> fallAlert = new HashMap<>();
                fallAlert.put("type", "FALL");
                fallAlert.put("message", "Fall detected by device!");
                fallAlert.put("timestamp", System.currentTimeMillis());
                fallAlert.put("status", "NEW");
                firestore.collection("users").document(userUid)
                        .collection("alerts").add(fallAlert);
            }

            FallEventPayload payload = new FallEventPayload(userId, true, 0, 0, 0, System.currentTimeMillis());
            apiService.sendFallEvent(token, payload).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) { }
                @Override
                public void onFailure(Call<Void> call, Throwable t) { }
            });
        } else if (msg.equals("SOS")) {
            Log.d(TAG, "SOS received from ESP32");
            if (userUid != null) {
                Map<String, Object> sosData = new HashMap<>();
                sosData.put("type", "SOS");
                sosData.put("message", "Emergency SOS triggered from device button!");
                sosData.put("timestamp", System.currentTimeMillis());
                sosData.put("status", "NEW");
                firestore.collection("users").document(userUid)
                        .collection("alerts").add(sosData);
            }
        } else if (msg.startsWith("SPO2:")) {
            try {
                int value = Integer.parseInt(msg.split(":")[1]);
                SpO2Payload payload = new SpO2Payload(userId, value, 0, System.currentTimeMillis());
                apiService.sendSpO2Reading(token, payload).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) { }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) { }
                });
            } catch (Exception e) {}
        }
        
        if (sendBroadcast) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, "BTChannel")
                .setContentTitle("Device Connection Active")
                .setContentText("Listening for health data")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(2, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(2, notification);
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "BTChannel",
                    "Device Connection Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        bluetoothService.stop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
