package com.example.elderlycare.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.Medicine;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MedicineMissedWorker extends Worker {
    private static final String TAG = "MedicineMissedWorker";

    public MedicineMissedWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        int medicineId = getInputData().getInt("medicineId", -1);
        String uid = getInputData().getString("uid");
        
        if (medicineId == -1 || uid == null) return Result.failure();

        DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
        Medicine medicine = dbHelper.getMedicineById(medicineId);

        if (medicine != null && !"Taken".equalsIgnoreCase(medicine.getStatus())) {
            Log.w(TAG, "Medicine MISSED: " + medicine.getName());
            
            // Create Alert in Firestore for Guardian
            Map<String, Object> alertData = new HashMap<>();
            alertData.put("type", "MEDICINE_MISSED");
            alertData.put("message", "Medicine missed: " + medicine.getName());
            alertData.put("timestamp", System.currentTimeMillis());
            alertData.put("status", "NEW");
            alertData.put("medicineName", medicine.getName());

            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .collection("alerts").add(alertData);
            
            return Result.success();
        }

        return Result.success();
    }
}
