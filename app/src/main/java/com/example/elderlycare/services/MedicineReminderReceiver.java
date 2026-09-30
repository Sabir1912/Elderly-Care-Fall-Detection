package com.example.elderlycare.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.elderlycare.R;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.Medicine;
import com.example.elderlycare.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class MedicineReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "MedReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        int slotIndex = intent.getIntExtra("slotIndex", -1);
        if (slotIndex == -1) return;

        DatabaseHelper dbHelper = new DatabaseHelper(context);
        SessionManager sessionManager = new SessionManager(context);
        int userId = sessionManager.getUserId();
        
        if (userId == -1) return;

        List<Medicine> allMeds = dbHelper.getMedicinesForUser(userId);
        List<String> medsToTake = new ArrayList<>();
        
        for (Medicine med : allMeds) {
            String timing = med.getTiming(); // "1-0-1-1"
            String[] parts = timing.split("-");
            if (slotIndex < parts.length && "1".equals(parts[slotIndex])) {
                // If the medicine is already marked as taken for today, we might skip it.
                // But for simplicity of reminder on ESP32, we send it.
                if (!"Taken".equalsIgnoreCase(med.getStatus())) {
                    medsToTake.add(med.getName());
                }
            }
        }

        if (!medsToTake.isEmpty()) {
            String combinedNames = String.join(",", medsToTake);
            Log.d(TAG, "Reminder for slot " + slotIndex + ": " + combinedNames);
            
            // 1. Send to ESP32 via BluetoothListenerService
            Intent btIntent = new Intent("com.example.elderlycare.ACTION_SEND_BT_DATA");
            btIntent.putExtra("data", "REMIND:" + combinedNames);
            context.sendBroadcast(btIntent);

            // 2. Show local notification
            showNotification(context, combinedNames);
        }
    }

    private void showNotification(Context context, String names) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "MedicineReminders";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Medicine Reminders", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Medicine Reminder")
                .setContentText("Time to take: " + names)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        manager.notify(names.hashCode(), notification);
    }
}
