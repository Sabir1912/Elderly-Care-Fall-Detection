package com.example.elderlycare.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.elderlycare.R;
import com.example.elderlycare.adapters.MedicineAdapter;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.Medicine;
import com.example.elderlycare.utils.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

public class MedicineListActivity extends AppCompatActivity {

    private RecyclerView rvMedicines;
    private MedicineAdapter adapter;
    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private FirebaseFirestore firestore;
    private List<Medicine> medicineList = new ArrayList<>();
    private int targetUserId;
    private String targetFirebaseUid;
    private ListenerRegistration medicinesListener;

    private TextView tvProgressText;
    private ProgressBar progressBarMedicines;
    private FloatingActionButton fabAddMedicine;
    private ImageView btnBack;
    private View layoutEmptyState;
    
    private BarChart barChartMedicines;
    private PieChart pieChartAdherence;
    private View layoutGuardianReport;
    private View layoutElderlySchedule;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_list);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new SessionManager(this);
        firestore = FirebaseFirestore.getInstance();

        targetUserId = sessionManager.getUserId();
        targetFirebaseUid = sessionManager.getFirebaseUid();

        if (getIntent().hasExtra("ELDERLY_ID")) {
            targetUserId = getIntent().getIntExtra("ELDERLY_ID", targetUserId);
        }
        if (getIntent().hasExtra("ELDERLY_UID")) {
            targetFirebaseUid = getIntent().getStringExtra("ELDERLY_UID");
        }

        initViews();
        setupListeners();
    }

    private void initViews() {
        rvMedicines = findViewById(R.id.rvMedicines);
        rvMedicines.setLayoutManager(new LinearLayoutManager(this));

        tvProgressText = findViewById(R.id.tvProgressText);
        progressBarMedicines = findViewById(R.id.progressBarMedicines);
        fabAddMedicine = findViewById(R.id.fabAddMedicine);
        btnBack = findViewById(R.id.btnBack);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        
        barChartMedicines = findViewById(R.id.barChartAdherence);
        pieChartAdherence = findViewById(R.id.pieChartAdherence);
        layoutGuardianReport = findViewById(R.id.layoutGuardianReport);
        layoutElderlySchedule = findViewById(R.id.layoutElderlySchedule);

        if ("Guardian".equals(sessionManager.getRole())) {
            fabAddMedicine.setVisibility(View.GONE);
            if (layoutGuardianReport != null) layoutGuardianReport.setVisibility(View.VISIBLE);
        } else {
            if (layoutGuardianReport != null) layoutGuardianReport.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        fabAddMedicine.setOnClickListener(v -> showAddMedicineBottomSheet());
    }

    private void showAddMedicineBottomSheet() {
        showManualAddDialog();
    }

    private void showManualAddDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_medicine_manual, null);
        builder.setView(view);

        EditText etName = view.findViewById(R.id.etMedicineName);
        EditText etDosage = view.findViewById(R.id.etDosage);
        EditText etNotes = view.findViewById(R.id.etNotes);
        EditText etStartDate = view.findViewById(R.id.etStartDate);
        EditText etEndDate = view.findViewById(R.id.etEndDate);
        
        EditText[] etTimes = new EditText[] {
            view.findViewById(R.id.etTime1),
            view.findViewById(R.id.etTime2),
            view.findViewById(R.id.etTime3),
            view.findViewById(R.id.etTime4)
        };

        setupDatePicker(etStartDate);
        setupDatePicker(etEndDate);
        for (EditText etTime : etTimes) {
            setupTimePicker(etTime);
        }

        MaterialButton btnSave = view.findViewById(R.id.btnSaveMedicine);

        AlertDialog dialog = builder.create();
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String dosage = etDosage.getText().toString().trim();
            String notes = etNotes.getText().toString().trim();
            String startDate = etStartDate.getText().toString().trim();
            String endDate = etEndDate.getText().toString().trim();

            if (name.isEmpty() || dosage.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
                Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder timesBuilder = new StringBuilder();
            for (EditText etTime : etTimes) {
                String t = etTime.getText().toString().trim();
                if (!t.isEmpty()) {
                    if (timesBuilder.length() > 0) timesBuilder.append(",");
                    timesBuilder.append(t);
                }
            }

            if (timesBuilder.length() == 0) {
                Toast.makeText(this, "Select at least one time", Toast.LENGTH_SHORT).show();
                return;
            }

            saveMedicine(name, dosage, timesBuilder.toString(), startDate, endDate, notes);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void setupDatePicker(EditText editText) {
        editText.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, day) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, day);
                editText.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime()));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void setupTimePicker(EditText editText) {
        editText.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new TimePickerDialog(this, (view, hour, minute) -> {
                editText.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        });
    }

    private void saveMedicine(String name, String dosage, String timing, String startDate, String endDate, String notes) {
        String[] timingSlots = timing.split(",");
        StringBuilder statusBuilder = new StringBuilder();
        for (int i = 0; i < timingSlots.length; i++) {
            statusBuilder.append("Pending");
            if (i < timingSlots.length - 1) statusBuilder.append(",");
        }

        if (targetFirebaseUid != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("name", name);
            data.put("dosage", dosage);
            data.put("timing", timing);
            data.put("status", statusBuilder.toString());
            data.put("startDate", startDate);
            data.put("endDate", endDate);
            data.put("notes", notes);
            data.put("timestamp", System.currentTimeMillis());

            firestore.collection("users").document(targetFirebaseUid)
                    .collection("medicines").add(data)
                    .addOnSuccessListener(doc -> Toast.makeText(this, "Medicine Added", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startRealtimeListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (medicinesListener != null) medicinesListener.remove();
    }

    private void startRealtimeListening() {
        if (targetFirebaseUid == null) return;

        medicinesListener = firestore.collection("users").document(targetFirebaseUid)
                .collection("medicines")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        medicineList.clear();
                        for (QueryDocumentSnapshot doc : value) {
                            Medicine med = doc.toObject(Medicine.class);
                            med.setId(doc.getId().hashCode());
                            medicineList.add(med);
                        }
                        updateUI();
                        if ("Guardian".equals(sessionManager.getRole())) updateAnalytics();
                    }
                });
    }

    private void updateAnalytics() {
        if (barChartMedicines == null || pieChartAdherence == null) return;

        int totalTaken = 0;
        int totalMissed = 0;
        int totalPending = 0;

        for (Medicine m : medicineList) {
            if (m.getStatus() == null) continue;
            for (String s : m.getStatus().split(",")) {
                if ("Taken".equalsIgnoreCase(s)) totalTaken++;
                else if ("Missed".equalsIgnoreCase(s)) totalMissed++;
                else totalPending++;
            }
        }

        // Pie Chart
        List<PieEntry> pieEntries = new ArrayList<>();
        if (totalTaken > 0) pieEntries.add(new PieEntry(totalTaken, "Taken"));
        if (totalMissed > 0) pieEntries.add(new PieEntry(totalMissed, "Missed"));
        if (totalPending > 0) pieEntries.add(new PieEntry(totalPending, "Pending"));

        PieDataSet pieDataSet = new PieDataSet(pieEntries, "");
        pieDataSet.setColors(new int[]{Color.GREEN, Color.RED, Color.GRAY});
        pieDataSet.setValueTextColor(Color.WHITE);
        pieDataSet.setValueTextSize(12f);
        pieChartAdherence.setData(new PieData(pieDataSet));
        pieChartAdherence.getDescription().setEnabled(false);
        pieChartAdherence.setCenterText("Adherence");
        pieChartAdherence.invalidate();

        // Bar Chart (Doses per Med)
        List<BarEntry> barEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < Math.min(medicineList.size(), 5); i++) {
            Medicine m = medicineList.get(i);
            int takenCount = 0;
            for(String s : m.getStatus().split(",")) if("Taken".equals(s)) takenCount++;
            barEntries.add(new BarEntry(i, takenCount));
            labels.add(m.getName());
        }

        BarDataSet barDataSet = new BarDataSet(barEntries, "Doses Taken");
        barDataSet.setColor(Color.BLUE);
        barChartMedicines.setData(new BarData(barDataSet));
        barChartMedicines.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChartMedicines.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChartMedicines.invalidate();
    }

    private void updateUI() {
        boolean isGuardian = "Guardian".equals(sessionManager.getRole());
        adapter = new MedicineAdapter(this, medicineList, isGuardian);
        rvMedicines.setAdapter(adapter);
        layoutEmptyState.setVisibility(medicineList.isEmpty() ? View.VISIBLE : View.GONE);
        updateProgress();
    }

    public void updateProgress() {
        int total = 0, taken = 0;
        for (Medicine m : medicineList) {
            if (m.getStatus() == null) continue;
            for (String s : m.getStatus().split(",")) {
                total++;
                if ("Taken".equalsIgnoreCase(s)) taken++;
            }
        }
        tvProgressText.setText(taken + "/" + total + " Doses Taken");
        progressBarMedicines.setProgress(total == 0 ? 0 : (taken * 100) / total);
    }
}
