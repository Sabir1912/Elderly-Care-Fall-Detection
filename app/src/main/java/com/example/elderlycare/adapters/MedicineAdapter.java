package com.example.elderlycare.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.elderlycare.R;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.Medicine;
import com.example.elderlycare.utils.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.MedicineViewHolder> {

    private final List<Medicine> medicineList;
    private final Context context;
    private final DatabaseHelper dbHelper;
    private final boolean isGuardianView;
    private final SessionManager sessionManager;

    public MedicineAdapter(Context context, List<Medicine> medicineList, boolean isGuardianView) {
        this.context = context;
        this.medicineList = medicineList;
        this.isGuardianView = isGuardianView;
        this.dbHelper = new DatabaseHelper(context);
        this.sessionManager = new SessionManager(context);
    }

    @NonNull
    @Override
    public MedicineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_medicine, parent, false);
        return new MedicineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MedicineViewHolder holder, int position) {
        Medicine medicine = medicineList.get(position);
        holder.tvName.setText(medicine.getName());
        
        String dateRange = (medicine.getStartDate() != null ? medicine.getStartDate() : "N/A") + 
                          " to " + 
                          (medicine.getEndDate() != null ? medicine.getEndDate() : "N/A");
        holder.tvDates.setText(dateRange);

        // Timing format: "1-0-1-1" (M-A-E-N)
        // Status format: "Pending-None-Taken-Pending"
        String[] timings = medicine.getTiming().split("-");
        String[] statuses = medicine.getStatus().split("-");

        setupCheckbox(holder.cbMorning, timings, statuses, 0, medicine, position);
        setupCheckbox(holder.cbAfternoon, timings, statuses, 1, medicine, position);
        setupCheckbox(holder.cbEvening, timings, statuses, 2, medicine, position);
        setupCheckbox(holder.cbNight, timings, statuses, 3, medicine, position);

        updateOverallStatus(holder.tvStatus, statuses, timings);

        // Long press to delete (Only for Elderly/Non-Guardian view)
        if (!isGuardianView) {
            holder.itemView.setOnLongClickListener(v -> {
                showDeleteConfirmation(medicine, position);
                return true;
            });
        }
    }

    private void showDeleteConfirmation(Medicine medicine, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Medicine")
                .setMessage("Are you sure you want to remove " + medicine.getName() + " from your schedule?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteMedicineFromAll(medicine, position);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteMedicineFromAll(Medicine medicine, int position) {
        // 1. Delete from Local DB
        dbHelper.deleteMedicine(medicine.getId());

        // 2. Delete from Cloud
        String uid = sessionManager.getFirebaseUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .collection("medicines")
                    .whereEqualTo("name", medicine.getName())
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                            doc.getReference().delete();
                        }
                    });
        }

        // 3. Update UI
        medicineList.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, medicineList.size());
        
        Toast.makeText(context, medicine.getName() + " deleted", Toast.LENGTH_SHORT).show();

        if (context instanceof com.example.elderlycare.activities.MedicineListActivity) {
            ((com.example.elderlycare.activities.MedicineListActivity) context).updateProgress();
        }
    }

    private void setupCheckbox(CheckBox cb, String[] timings, String[] statuses, int index, Medicine medicine, int position) {
        if (timings.length > index && "1".equals(timings[index])) {
            cb.setVisibility(View.VISIBLE);
            cb.setEnabled(!isGuardianView); 

            boolean isTaken = statuses.length > index && "Taken".equalsIgnoreCase(statuses[index]);
            cb.setChecked(isTaken);
            
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed()) { 
                    updateMedicineStatus(medicine, index, isChecked, position);
                }
            });
        } else {
            cb.setVisibility(View.GONE);
        }
    }

    private void updateMedicineStatus(Medicine medicine, int index, boolean isChecked, int position) {
        String[] statuses = medicine.getStatus().split("-");
        if (statuses.length > index) {
            statuses[index] = isChecked ? "Taken" : "Pending";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < statuses.length; i++) {
            sb.append(statuses[i]).append(i < statuses.length - 1 ? "-" : "");
        }
        String newStatusString = sb.toString();
        medicine.setStatus(newStatusString);

        dbHelper.updateMedicineStatus(medicine.getId(), newStatusString);

        String uid = sessionManager.getFirebaseUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .collection("medicines")
                    .whereEqualTo("name", medicine.getName())
                    .limit(1)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            queryDocumentSnapshots.getDocuments().get(0).getReference()
                                    .update("status", newStatusString);
                        }
                    });
        }

        Toast.makeText(context, "Status updated", Toast.LENGTH_SHORT).show();
        
        if (context instanceof com.example.elderlycare.activities.MedicineListActivity) {
            ((com.example.elderlycare.activities.MedicineListActivity) context).updateProgress();
        }
        
        notifyItemChanged(position);
    }

    private void updateOverallStatus(TextView tvStatus, String[] statuses, String[] timings) {
        int total = 0;
        int taken = 0;
        for (int i = 0; i < timings.length; i++) {
            if ("1".equals(timings[i])) {
                total++;
                if (statuses.length > i && "Taken".equalsIgnoreCase(statuses[i])) {
                    taken++;
                }
            }
        }

        if (taken == total && total > 0) {
            tvStatus.setText("All Doses Taken");
            tvStatus.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            tvStatus.setText(taken + "/" + total + " Doses Taken");
            tvStatus.setTextColor(Color.parseColor("#FF9800"));
        }
    }

    @Override
    public int getItemCount() {
        return medicineList.size();
    }

    public static class MedicineViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDates, tvStatus;
        CheckBox cbMorning, cbAfternoon, cbEvening, cbNight;

        public MedicineViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvMedicineName);
            tvDates = itemView.findViewById(R.id.tvMedicineDates);
            tvStatus = itemView.findViewById(R.id.tvMedicineStatus);
            cbMorning = itemView.findViewById(R.id.cbMorning);
            cbAfternoon = itemView.findViewById(R.id.cbAfternoon);
            cbEvening = itemView.findViewById(R.id.cbEvening);
            cbNight = itemView.findViewById(R.id.cbNight);
        }
    }
}
