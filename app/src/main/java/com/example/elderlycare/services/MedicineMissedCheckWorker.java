package com.example.elderlycare.services;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.utils.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class MedicineMissedCheckWorker extends Worker {

    public MedicineMissedCheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        int logId = getInputData().getInt("log_id", -1);
        String medicineName = getInputData().getString("medicine_name");

        if (logId == -1) return Result.failure();

        DatabaseHelper db = new DatabaseHelper(getApplicationContext());
        android.content.ContentValues log = null;
        java.util.List<android.content.ContentValues> logs = db.getMedicineLogsForToday(
                new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date()));
        
        for (android.content.ContentValues l : logs) {
            if (l.getAsInteger("id") == logId) {
                log = l;
                break;
            }
        }

        if (log != null && "Pending".equalsIgnoreCase(log.getAsString("status"))) {
            // Mark as missed locally
            db.updateMedicineLogStatus(logId, "Missed");

            // Notify Guardian via Firestore
            SessionManager sessionManager = new SessionManager(getApplicationContext());
            String uid = sessionManager.getFirebaseUid();
            if (uid != null) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("type", "MISSED_MEDICINE");
                alert.put("message", "Elderly missed medicine: " + medicineName);
                alert.put("timestamp", System.currentTimeMillis());
                alert.put("status", "NEW");
                
                FirebaseFirestore.getInstance().collection("users").document(uid)
                        .collection("alerts").add(alert);
            }
        }

        return Result.success();
    }
}
