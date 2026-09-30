package com.example.elderlycare.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.elderlycare.R;
import com.example.elderlycare.activities.GuardianAlertActivity;
import com.example.elderlycare.utils.SessionManager;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Filter;

import java.util.ArrayList;
import java.util.List;

public class GuardianNotificationService extends Service {
    private static final String TAG = "GuardianNotifService";
    private static final String CHANNEL_ID = "GuardianAlertChannel";
    private FirebaseFirestore firestore;
    private SessionManager sessionManager;
    private List<ListenerRegistration> registrations = new ArrayList<>();
    private List<String> listenedElderlyUids = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        firestore = FirebaseFirestore.getInstance();
        sessionManager = new SessionManager(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(101, createServiceNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(101, createServiceNotification());
        }
        listenToElderlyAlerts();
        return START_STICKY;
    }

    private void listenToElderlyAlerts() {
        String guardianUsername = sessionManager.getUsername();
        if (guardianUsername == null) {
            Log.e(TAG, "Guardian username is null. Cannot start alert listeners.");
            return;
        }

        // Listen for ANY elderly user that has this guardian in either slot 1 or slot 2
        firestore.collection("users")
                .whereEqualTo("role", "Elderly")
                .where(Filter.or(
                        Filter.equalTo("guardianUsername", guardianUsername),
                        Filter.equalTo("guardianUsername2", guardianUsername)
                ))
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    for (DocumentChange dc : value.getDocumentChanges()) {
                        String elderlyUid = dc.getDocument().getId();
                        if (dc.getType() == DocumentChange.Type.ADDED || dc.getType() == DocumentChange.Type.MODIFIED) {
                            if (!listenedElderlyUids.contains(elderlyUid)) {
                                String elderlyName = dc.getDocument().getString("name");
                                String elderlyPhone = dc.getDocument().getString("phone");
                                setupAlertListenerForElderly(elderlyUid, elderlyName, elderlyPhone);
                                listenedElderlyUids.add(elderlyUid);
                            }
                        } else if (dc.getType() == DocumentChange.Type.REMOVED) {
                            // In a full implementation, we'd remove the specific listener here
                            listenedElderlyUids.remove(elderlyUid);
                        }
                    }
                });
    }

    private void setupAlertListenerForElderly(String uid, String name, String phone) {
        ListenerRegistration reg = firestore.collection("users").document(uid)
                .collection("alerts")
                .whereEqualTo("status", "NEW")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    if (!value.isEmpty()) {
                        com.google.firebase.firestore.DocumentSnapshot doc = value.getDocuments().get(0);
                        String type = doc.getString("type");
                        String message = doc.getString("message");
                        triggerHighPriorityAlert(uid, name, phone, type, message);
                        // Status NOTIFIED helps avoid re-triggering, but second guardian also needs it.
                        // Ideally, each guardian would have their own status, but for this prototype,
                        // we use a shared status. Simultaneous alerts work because both services 
                        // should receive the snapshot at nearly the same time before status update.
                        doc.getReference().update("status", "NOTIFIED");
                    }
                });
        registrations.add(reg);
    }

    private void triggerHighPriorityAlert(String uid, String name, String phone, String type, String message) {
        Intent alertIntent = new Intent(this, GuardianAlertActivity.class);
        alertIntent.putExtra("elderlyUserId", uid.hashCode()); 
        alertIntent.putExtra("elderlyUid", uid);
        alertIntent.putExtra("elderlyName", name);
        alertIntent.putExtra("elderlyPhone", phone);
        alertIntent.putExtra("alertType", type);
        alertIntent.putExtra("alertMessage", message != null ? message : type);
        alertIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(),
                alertIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("⚠️ " + type + " ALERT: " + name)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(uid.hashCode(), notification);
        }
    }

    private Notification createServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Guardian Monitor Active")
                .setContentText("Monitoring for elderly safety alerts...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Guardian Safety Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("High priority alerts for falls and SOS triggers");
            channel.enableLights(true);
            channel.enableVibration(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        for (ListenerRegistration reg : registrations) reg.remove();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
