package com.example.elderlycare.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.elderlycare.R;
import com.example.elderlycare.api.ApiClient;
import com.example.elderlycare.api.ApiService;
import com.example.elderlycare.api.models.ApiResponse;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.Medicine;
import com.example.elderlycare.models.User;
import com.example.elderlycare.utils.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
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

import java.util.ArrayList;
import java.util.List;

public class MedicineChecklistActivity extends AppCompatActivity {

    private ImageView btnBack;
    private PieChart pieChartAdherence;
    private BarChart barChartWeekly;
    private RecyclerView rvMedicineSchedule;

    private SessionManager sessionManager;
    private DatabaseHelper dbHelper;
    private User currentUser, elderlyUser;

    private boolean hasLinkedUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_checklist);

        hasLinkedUser = getIntent().getBooleanExtra("hasLinkedUser", false);

        sessionManager = new SessionManager(this);
        dbHelper = new DatabaseHelper(this);

        int userId = sessionManager.getUserId();
        if (userId != -1) {
            currentUser = dbHelper.getUserById(userId);
        }
        
        if (currentUser == null) {
            currentUser = new User();
            currentUser.setUsername(sessionManager.getUsername());
            currentUser.setRole(sessionManager.getRole());
        }

        if (hasLinkedUser && currentUser.getUsername() != null) {
            List<User> elderlyList = dbHelper.getElderlyForGuardian(currentUser.getUsername());
            if (!elderlyList.isEmpty()) {
                elderlyUser = elderlyList.get(0);
            }
        }

        if (elderlyUser == null && hasLinkedUser) {
            elderlyUser = new User();
            elderlyUser.setId(getIntent().getIntExtra("ELDERLY_ID", -1));
            elderlyUser.setName(getIntent().getStringExtra("ELDERLY_NAME"));
        }

        initViews();
        setupListeners();
        setupPieChart();
        setupBarChart();
        loadMedicineSchedule();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        pieChartAdherence = findViewById(R.id.pieChartAdherence);
        barChartWeekly = findViewById(R.id.barChartWeekly);
        rvMedicineSchedule = findViewById(R.id.rvMedicineSchedule);
        rvMedicineSchedule.setLayoutManager(new LinearLayoutManager(this));
        rvMedicineSchedule.setNestedScrollingEnabled(false);

        View layoutEmptyState = findViewById(R.id.layoutEmptyState);
        View cardAdherence = findViewById(R.id.cardAdherence);
        View cardWeeklyReport = findViewById(R.id.cardWeeklyReport);
        View cardSchedule = findViewById(R.id.cardSchedule);

        if (hasLinkedUser) {
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
            if (cardAdherence != null) cardAdherence.setVisibility(View.VISIBLE);
            if (cardWeeklyReport != null) cardWeeklyReport.setVisibility(View.VISIBLE);
            if (cardSchedule != null) cardSchedule.setVisibility(View.VISIBLE);
        } else {
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
            if (cardAdherence != null) cardAdherence.setVisibility(View.GONE);
            if (cardWeeklyReport != null) cardWeeklyReport.setVisibility(View.GONE);
            if (cardSchedule != null) cardSchedule.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupPieChart() {
        List<PieEntry> entries = new ArrayList<>();
        if (hasLinkedUser) {
            // Mock data: 75% taken, 25% missed (per screenshot)
            entries.add(new PieEntry(75f, ""));
            entries.add(new PieEntry(25f, ""));
        } else {
            entries.add(new PieEntry(100f, "")); // empty state
        }

        PieDataSet dataSet = new PieDataSet(entries, "Adherence");
        if (hasLinkedUser) {
            dataSet.setColors(new int[]{Color.parseColor("#21C063"), Color.parseColor("#F44336")});
        } else {
            dataSet.setColors(new int[]{Color.parseColor("#E0E0E0")}); // Gray for no data
        }
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);

        PieData data = new PieData(dataSet);
        data.setDrawValues(false); // Hide text on slices

        pieChartAdherence.setData(data);
        pieChartAdherence.getDescription().setEnabled(false);
        pieChartAdherence.setDrawHoleEnabled(true);
        pieChartAdherence.setHoleColor(Color.TRANSPARENT);
        pieChartAdherence.setTransparentCircleRadius(61f);
        pieChartAdherence.setHoleRadius(58f); // Doughnut style
        pieChartAdherence.getLegend().setEnabled(false); // We have custom legend below it
        pieChartAdherence.invalidate();
    }

    private void setupBarChart() {
        List<BarEntry> entries = new ArrayList<>();
        if (hasLinkedUser) {
            // Mock data: Tue: 75, Wed: 100, Thu: 65, Fri: 100, Sat: 75, Sun: 75
            entries.add(new BarEntry(0, 100f)); // Mon
            entries.add(new BarEntry(1, 75f)); // Tue
            entries.add(new BarEntry(2, 100f)); // Wed
            entries.add(new BarEntry(3, 65f)); // Thu
            entries.add(new BarEntry(4, 100f)); // Fri
            entries.add(new BarEntry(5, 75f)); // Sat
            entries.add(new BarEntry(6, 75f)); // Sun
        } else {
            for (int i=0; i<7; i++) {
                entries.add(new BarEntry(i, 0f));
            }
        }

        BarDataSet dataSet = new BarDataSet(entries, "Weekly Adherence");
        if (hasLinkedUser) {
            dataSet.setColor(Color.parseColor("#21C063"));
        } else {
            dataSet.setColor(Color.parseColor("#E0E0E0"));
        }
        dataSet.setDrawValues(false);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);
        barChartWeekly.setData(data);
        barChartWeekly.setFitBars(true);
        barChartWeekly.getDescription().setEnabled(false);
        barChartWeekly.getLegend().setEnabled(false);
        
        // Setup X-Axis
        String[] days = new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        XAxis xAxis = barChartWeekly.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(days));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        // Setup Y-Axis
        barChartWeekly.getAxisRight().setEnabled(false);
        barChartWeekly.getAxisLeft().setAxisMinimum(0f);
        barChartWeekly.getAxisLeft().setAxisMaximum(100f);
        barChartWeekly.getAxisLeft().setDrawGridLines(true);
        barChartWeekly.getAxisLeft().enableGridDashedLine(10f, 10f, 0f);

        barChartWeekly.invalidate();
    }

    private void loadMedicineSchedule() {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        List<android.content.ContentValues> logs = dbHelper.getMedicineLogsForToday(today);
        List<ScheduleCard> cards = new java.util.HashMap<Integer, ScheduleCard>().values().stream().collect(ArrayList::new, ArrayList::add, ArrayList::addAll); // dummy
        
        if (!logs.isEmpty()) {
            java.util.Map<Integer, ScheduleCard> cardMap = new java.util.HashMap<>();
            for (android.content.ContentValues log : logs) {
                int medId = log.getAsInteger("medicineId");
                ScheduleCard card = cardMap.get(medId);
                if (card == null) {
                    Medicine m = dbHelper.getMedicineById(medId);
                    card = new ScheduleCard(m != null ? m.getName() : "Medicine", m != null ? m.getDosage() : "1 Pill");
                    cardMap.put(medId, card);
                }
                card.times.add(new ScheduleTime(log.getAsString("timingLabel"), log.getAsString("status"), log.getAsInteger("id")));
            }
            cards = new ArrayList<>(cardMap.values());
            rvMedicineSchedule.setAdapter(new ScheduleAdapter(cards));
            return;
        }

        if (!hasLinkedUser || elderlyUser == null) {
            ScheduleAdapter adapter = new ScheduleAdapter(new ArrayList<>());
            rvMedicineSchedule.setAdapter(adapter);
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        String token = "Bearer sample_token"; 
        
        apiService.getMedicines(token, elderlyUser.getId()).enqueue(new Callback<ApiResponse<List<Medicine>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Medicine>>> call, Response<ApiResponse<List<Medicine>>> response) {
                List<ScheduleCard> cards = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<Medicine> medicineList = response.body().getData();
                    if (medicineList != null) {
                        for (Medicine m : medicineList) {
                            ScheduleCard card = new ScheduleCard(m.getName(), m.getDosage() != null ? m.getDosage() : "1 Pill");
                            
                            // Map timings: e.g. "1-0-1-0" to Morning and Evening
                            if (m.getTiming() != null) {
                                String[] t = m.getTiming().split("-");
                                if (t.length == 4) {
                                    if (t[0].equals("1")) card.times.add(new ScheduleTime("Morning", m.getStatus()));
                                    if (t[1].equals("1")) card.times.add(new ScheduleTime("Afternoon", m.getStatus()));
                                    if (t[2].equals("1")) card.times.add(new ScheduleTime("Evening", m.getStatus()));
                                    if (t[3].equals("1")) card.times.add(new ScheduleTime("Night", m.getStatus()));
                                }
                            }
                            if(card.times.isEmpty()) {
                                // Fallback
                                card.times.add(new ScheduleTime("Scheduled", m.getStatus()));
                            }
                            cards.add(card);
                        }
                    }
                }
                ScheduleAdapter adapter = new ScheduleAdapter(cards);
                rvMedicineSchedule.setAdapter(adapter);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Medicine>>> call, Throwable t) {
                // Keep empty on failure for now
                ScheduleAdapter adapter = new ScheduleAdapter(new ArrayList<>());
                rvMedicineSchedule.setAdapter(adapter);
            }
        });
    }

    // --- Inner Classes for Schedule List ---
    static class ScheduleTime {
        String time;
        String status;
        int logId;
        ScheduleTime(String time, String status, int logId) {
            this.time = time;
            this.status = status;
            this.logId = logId;
        }
        ScheduleTime(String time, String status) {
            this.time = time;
            this.status = status;
            this.logId = -1;
        }
    }

    static class ScheduleCard {
        String name;
        String dosage;
        List<ScheduleTime> times = new ArrayList<>();
        ScheduleCard(String name, String dosage) {
            this.name = name;
            this.dosage = dosage;
        }
    }

    class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {
        private List<ScheduleCard> items;

        ScheduleAdapter(List<ScheduleCard> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_medicine_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ScheduleCard item = items.get(position);
            holder.tvMedicineName.setText(item.name);
            holder.tvMedicineDosage.setText(item.dosage);

            holder.llTimesContainer.removeAllViews();
            for (ScheduleTime t : item.times) {
                View timeView = LayoutInflater.from(holder.itemView.getContext())
                        .inflate(R.layout.item_medicine_time, holder.llTimesContainer, false);
                TextView tvTime = timeView.findViewById(R.id.tvMedicineTime);
                TextView tvStatus = timeView.findViewById(R.id.tvMedicineStatus);
                
                tvTime.setText(t.time);
                tvStatus.setText(t.status);
                
                if (t.status.equalsIgnoreCase("Taken")) {
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"));
                } else if (t.status.equalsIgnoreCase("Pending") || t.status.equalsIgnoreCase("Missed")) {
                    tvStatus.setTextColor(Color.parseColor("#F44336"));
                    if (!hasLinkedUser) { // If it's the elderly user themselves
                        timeView.setOnClickListener(v -> {
                            dbHelper.updateMedicineLogStatus(t.logId, "Taken");
                            
                            // Push to Firestore for Guardian to see
                            String uid = sessionManager.getFirebaseUid();
                            if (uid != null) {
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    .collection("users").document(uid)
                                    .collection("medicine_reports").add(new java.util.HashMap<String, Object>() {{
                                        put("medicineId", item.name);
                                        put("status", "Taken");
                                        put("timestamp", System.currentTimeMillis());
                                    }});
                            }
                            
                            loadMedicineSchedule(); // Refresh
                        });
                    }
                }
                
                holder.llTimesContainer.addView(timeView);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvMedicineName, tvMedicineDosage;
            LinearLayout llTimesContainer;

            ViewHolder(View itemView) {
                super(itemView);
                tvMedicineName = itemView.findViewById(R.id.tvMedicineName);
                tvMedicineDosage = itemView.findViewById(R.id.tvMedicineDosage);
                llTimesContainer = itemView.findViewById(R.id.llTimesContainer);
            }
        }
    }
}
