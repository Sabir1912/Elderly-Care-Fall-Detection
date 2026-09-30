package com.example.elderlycare.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.elderlycare.R;
import com.example.elderlycare.activities.SplashActivity;

public class MedicineReminderWorker extends Worker {

    public static final String KEY_MEDICINE_NAME = "medicine_name";
    public static final String KEY_DOSAGE = "dosage";

    public MedicineReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String medicineName = getInputData().getString(KEY_MEDICINE_NAME);
        String dosage = getInputData().getString(KEY_DOSAGE);
        int medicineId = getInputData().getInt("medicine_id", -1);
        String timingLabel = getInputData().getString("timing_label"); // e.g., "Morning"

        // 1. Show Phone Notification
        showNotification(medicineName, dosage);

        // 2. Send to ESP32 via Bluetooth
        Intent btIntent = new Intent("com.example.elderlycare.ACTION_SEND_BT_DATA");
        btIntent.putExtra("data", "MED:" + medicineName + "|" + dosage + "\n");
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(btIntent);

        // 2b. Send to ESP32 via WiFi (HTTP POST)
        String lastIp = new com.example.elderlycare.utils.SessionManager(getApplicationContext()).getLastEspIp();
        if (lastIp != null && !lastIp.isEmpty()) {
            new com.example.elderlycare.network.Esp32HttpHelper(lastIp).sendMedicineReminderHttp(medicineName + "|" + dosage);
        }

        // 3. Log into Database for Checklist
        com.example.elderlycare.database.DatabaseHelper db = new com.example.elderlycare.database.DatabaseHelper(getApplicationContext());
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("medicineId", medicineId);
        values.put("timingLabel", timingLabel);
        values.put("scheduledTime", System.currentTimeMillis());
        values.put("status", "Pending");
        values.put("date", new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date()));
        
        android.database.sqlite.SQLiteDatabase sdb = db.getWritableDatabase();
        long logId = sdb.insert("medicine_logs", null, values);

        // 4. Schedule Missed Dose Check (1 hour later)
        androidx.work.OneTimeWorkRequest missedCheckRequest = new androidx.work.OneTimeWorkRequest.Builder(MedicineMissedCheckWorker.class)
                .setInitialDelay(1, java.util.concurrent.TimeUnit.HOURS)
                .setInputData(new androidx.work.Data.Builder()
                        .putInt("log_id", (int)logId)
                        .putString("medicine_name", medicineName)
                        .build())
                .build();
        androidx.work.WorkManager.getInstance(getApplicationContext()).enqueue(missedCheckRequest);

        return Result.success();
    }

    private void showNotification(String medicineName, String dosage) {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "medicine_reminders";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Medicine Reminders", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(getApplicationContext(), SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Time to take your Medicine!")
                .setContentText(medicineName + " - " + dosage)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Required permission logic is assumed handled in Manifest/Runtime
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
