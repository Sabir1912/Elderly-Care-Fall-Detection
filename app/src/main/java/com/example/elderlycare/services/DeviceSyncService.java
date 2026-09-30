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

import com.example.elderlycare.R;
import com.example.elderlycare.network.Esp32HttpHelper;
import com.example.elderlycare.utils.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;

public class DeviceSyncService extends Service {
    private static final String TAG = "DeviceSyncService";
    private static final int NOTIFICATION_ID = 1234;
    private static final String CHANNEL_ID = "DeviceSyncChannel";
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable syncRunnable;
    private SessionManager sessionManager;
    private FirebaseFirestore firestore;
    private Esp32HttpHelper httpHelper;
    private String lastIp = "";
    private boolean isWebSocketActive = false;

    @Override
    public void onCreate() {
        super.onCreate();
        sessionManager = new SessionManager(this);
        firestore = FirebaseFirestore.getInstance();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification("Monitoring device connection..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startSyncTask();
        return START_STICKY;
    }

    private void startSyncTask() {
        if (syncRunnable != null) handler.removeCallbacks(syncRunnable);
        
        syncRunnable = new Runnable() {
            @Override
            public void run() {
                String ip = sessionManager.getLastEspIp();
                if (ip != null && !ip.isEmpty()) {
                    
                    // If IP changed or helper is null, re-initialize
                    if (httpHelper == null || !ip.equals(lastIp)) {
                        Log.d(TAG, "Initializing connection to: " + ip);
                        lastIp = ip;
                        if (httpHelper != null) httpHelper.disconnect();
                        httpHelper = new Esp32HttpHelper(ip);
                        isWebSocketActive = false;
                    }

                    // 1. Maintain WebSocket connection (this makes OLED show [C])
                    if (!isWebSocketActive) {
                        attemptWebSocketConnect();
                    }

                    // 2. Periodic HTTP health check (backup status provider)
                    httpHelper.syncStates(new Esp32HttpHelper.StateCallback() {
                        @Override
                        public void onSuccess(JSONArray states) {
                            // If HTTP is reachable, we are online
                            updateCloudStatus(1);
                            updateNotification("Device: Online (WiFi)");
                        }

                        @Override
                        public void onError(String error) {
                            // If HTTP fails, the device is unreachable. Force a WS reset
                            // so the next loop cycle attempts a fresh reconnection.
                            isWebSocketActive = false;
                            updateCloudStatus(0);
                            updateNotification("Device: Disconnected");
                        }
                    });
                } else {
                    updateCloudStatus(0);
                    updateNotification("No device configured");
                }
                handler.postDelayed(this, 10000); // Check every 10s to be polite to ESP32
            }
        };
        handler.post(syncRunnable);
    }

    private void attemptWebSocketConnect() {
        Log.d(TAG, "Attempting WebSocket connection...");
        httpHelper.connect(new Esp32HttpHelper.MessageListener() {
            @Override
            public void onMessageReceived(String message) {
                Log.d(TAG, "WS Msg: " + message);
                // We could parse global alerts here if needed
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "WS Error: " + error);
                isWebSocketActive = false;
            }

            @Override
            public void onConnected() {
                Log.d(TAG, "WS Connected! OLED should now show [C]");
                isWebSocketActive = true;
                updateCloudStatus(1);
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "WS Disconnected");
                isWebSocketActive = false;
            }
        });
    }

    private void updateCloudStatus(int status) {
        String uid = sessionManager.getFirebaseUid();
        if (uid != null) {
            firestore.collection("users").document(uid)
                    .update("isMinderConnected", status, "lastStatusUpdate", System.currentTimeMillis());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Device Sync Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification getNotification(String content) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Minder Safety Link")
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, getNotification(content));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (syncRunnable != null) handler.removeCallbacks(syncRunnable);
        if (httpHelper != null) httpHelper.disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}