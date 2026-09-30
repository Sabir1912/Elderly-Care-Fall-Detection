package com.example.elderlycare.utils;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.Medicine;
import com.example.elderlycare.services.MedicineReminderReceiver;
import com.example.elderlycare.workers.MedicineMissedWorker;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MedicineReminderManager {
    private static final String TAG = "MedReminderManager";
    private static final int[] SLOT_HOURS = {8, 13, 18, 21}; // Morning, Afternoon, Evening, Night

    public static void setReminder(Context context, Medicine medicine) {
        // When a new medicine is added, we just reschedule all slot alarms to be safe
        // and schedule missed checks for this specific medicine.
        rescheduleAllAlarms(context);
        
        String timing = medicine.getTiming(); 
        String[] parts = timing.split("-");
        for (int i = 0; i < parts.length; i++) {
            if ("1".equals(parts[i])) {
                scheduleMissedCheck(context, medicine, SLOT_HOURS[i]);
            }
        }
    }

    public static void rescheduleAllAlarms(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        for (int i = 0; i < SLOT_HOURS.length; i++) {
            scheduleSlotAlarm(context, alarmManager, i, SLOT_HOURS[i]);
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private static void scheduleSlotAlarm(Context context, AlarmManager alarmManager, int slotIndex, int hour) {
        Intent intent = new Intent(context, MedicineReminderReceiver.class);
        intent.putExtra("slotIndex", slotIndex);

        // Unique request code for each slot
        int requestCode = 2000 + slotIndex;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        Log.d(TAG, "Scheduled slot alarm " + slotIndex + " at " + hour + ":00");
    }

    private static void scheduleMissedCheck(Context context, Medicine medicine, int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour + 1); // Check 1 hour after scheduled time
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        long delay = calendar.getTimeInMillis() - System.currentTimeMillis();
        if (delay < 0) delay += TimeUnit.DAYS.toMillis(1);

        SessionManager sessionManager = new SessionManager(context);
        String uid = sessionManager.getFirebaseUid();

        Data inputData = new Data.Builder()
                .putInt("medicineId", medicine.getId())
                .putString("uid", uid)
                .build();

        OneTimeWorkRequest missedWork = new OneTimeWorkRequest.Builder(MedicineMissedWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("MISSED_" + medicine.getId() + "_" + hour)
                .build();

        WorkManager.getInstance(context).enqueue(missedWork);
    }
}
