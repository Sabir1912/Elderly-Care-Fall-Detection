package com.example.elderlycare.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.elderlycare.R;
import com.example.elderlycare.api.ApiClient;
import com.example.elderlycare.api.ApiService;
import com.example.elderlycare.api.models.ApiResponse;
import com.example.elderlycare.api.models.DashboardAnalytics;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.User;
import com.example.elderlycare.utils.SessionManager;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WeeklyReportsActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView tvReportSubtitle;
    private BarChart barChartAdherence;
    private LineChart lineChartActivity;

    // New TextViews for real-time data
    private TextView tvDateRange;
    private TextView tvAvgAdherence;
    private TextView tvActiveDays;
    private TextView tvLocationsVisited;
    private TextView tvAdherenceSummary;
    private TextView tvActivitySummary;
    private TextView tvSummaryMedicinesTaken;
    private TextView tvSummaryActiveDays;
    private TextView tvSummaryAverageSteps;
    private TextView tvSummaryHospitalVisits;


    private SessionManager sessionManager;
    private DatabaseHelper dbHelper;
    private User currentUser, elderlyUser;

    private boolean hasLinkedUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weekly_reports);

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
        fetchAnalyticsFromCloud();
    }

    private void fetchAnalyticsFromCloud() {
        if (!hasLinkedUser || elderlyUser == null) {
            updateUIWithAnalytics(null);
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        String token = "Bearer sample_token"; 
        
        apiService.getGuardianDashboardData(token, elderlyUser.getId()).enqueue(new Callback<ApiResponse<DashboardAnalytics>>() {
            @Override
            public void onResponse(Call<ApiResponse<DashboardAnalytics>> call, Response<ApiResponse<DashboardAnalytics>> response) {
                if(response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    DashboardAnalytics data = response.body().getData();
                    updateUIWithAnalytics(data);
                } else {
                    updateUIWithAnalytics(null);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<DashboardAnalytics>> call, Throwable t) {
                updateUIWithAnalytics(null);
            }
        });
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvReportSubtitle = findViewById(R.id.tvReportSubtitle);
        barChartAdherence = findViewById(R.id.barChartAdherence);
        lineChartActivity = findViewById(R.id.lineChartActivity);

        // Initialize new TextViews
        tvDateRange = findViewById(R.id.tvDateRange);
        tvAvgAdherence = findViewById(R.id.tvAvgAdherence);
        tvActiveDays = findViewById(R.id.tvActiveDays);
        tvLocationsVisited = findViewById(R.id.tvLocationsVisited);
        tvAdherenceSummary = findViewById(R.id.tvAdherenceSummary);
        tvActivitySummary = findViewById(R.id.tvActivitySummary);
        tvSummaryMedicinesTaken = findViewById(R.id.tvSummaryMedicinesTaken);
        tvSummaryActiveDays = findViewById(R.id.tvSummaryActiveDays);
        tvSummaryAverageSteps = findViewById(R.id.tvSummaryAverageSteps);
        tvSummaryHospitalVisits = findViewById(R.id.tvSummaryHospitalVisits);

        android.view.View layoutEmptyState = findViewById(R.id.layoutEmptyState);
        android.view.View cardDateRow = findViewById(R.id.cardDateRow);
        android.view.View layoutMiniCards = findViewById(R.id.layoutMiniCards);
        android.view.View cardMedicineAdherence = findViewById(R.id.cardMedicineAdherence);
        android.view.View cardDailyActivity = findViewById(R.id.cardDailyActivity);
        android.view.View cardWeeklySummary = findViewById(R.id.cardWeeklySummary);
        android.view.View cardAboutReports = findViewById(R.id.cardAboutReports);

        if (hasLinkedUser && elderlyUser != null) {
            tvReportSubtitle.setText(elderlyUser.getName() + "'s health analytics");
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(android.view.View.GONE);
            if (cardDateRow != null) cardDateRow.setVisibility(android.view.View.VISIBLE);
            if (layoutMiniCards != null) layoutMiniCards.setVisibility(android.view.View.VISIBLE);
            if (cardMedicineAdherence != null) cardMedicineAdherence.setVisibility(android.view.View.VISIBLE);
            if (cardDailyActivity != null) cardDailyActivity.setVisibility(android.view.View.VISIBLE);
            if (cardWeeklySummary != null) cardWeeklySummary.setVisibility(android.view.View.VISIBLE);
            if (cardAboutReports != null) cardAboutReports.setVisibility(android.view.View.VISIBLE);
        } else {
            tvReportSubtitle.setText("No User Linked");
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(android.view.View.VISIBLE);
            if (cardDateRow != null) cardDateRow.setVisibility(android.view.View.GONE);
            if (layoutMiniCards != null) layoutMiniCards.setVisibility(android.view.View.GONE);
            if (cardMedicineAdherence != null) cardMedicineAdherence.setVisibility(android.view.View.GONE);
            if (cardDailyActivity != null) cardDailyActivity.setVisibility(android.view.View.GONE);
            if (cardWeeklySummary != null) cardWeeklySummary.setVisibility(android.view.View.GONE);
            if (cardAboutReports != null) cardAboutReports.setVisibility(android.view.View.GONE);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
    }
    
    private void updateUIWithAnalytics(DashboardAnalytics data) {
        // Date Range
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
        String endDate = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, -6);
        String startDate = sdf.format(cal.getTime());
        tvDateRange.setText(startDate + " - " + endDate);

        if (data != null) {
            // Mini Cards
            tvAvgAdherence.setText(String.format(Locale.getDefault(), "%.0f%%", data.getMedicineCompliancePercent()));
            tvActiveDays.setText("N/A"); // Data not available from API
            tvLocationsVisited.setText("N/A"); // Data not available from API

            // Medicine Adherence Chart Card
            tvAdherenceSummary.setText(String.format(Locale.getDefault(), "%.0f%% average adherence this week", data.getMedicineCompliancePercent()));

            // Daily Activity Line Chart Card
            tvActivitySummary.setText("N/A steps today"); // Data not available from API

            // Weekly Summary List
            tvSummaryMedicinesTaken.setText(data.getTakenMedicines() + " of " + data.getTotalMedicines());
            tvSummaryActiveDays.setText("N/A"); // Data not available from API
            tvSummaryAverageSteps.setText("N/A"); // Data not available from API
            tvSummaryHospitalVisits.setText("N/A"); // Data not available from API

            // Setup Charts with available real-time data
            setupBarChart(data);
            setupLineChart(data);
        } else {
            // Set default values if data is null
            tvAvgAdherence.setText("N/A");
            tvActiveDays.setText("N/A");
            tvLocationsVisited.setText("N/A");
            tvAdherenceSummary.setText("N/A average adherence this week");
            tvActivitySummary.setText("N/A steps today");
            tvSummaryMedicinesTaken.setText("N/A");
            tvSummaryActiveDays.setText("N/A");
            tvSummaryAverageSteps.setText("N/A");
            tvSummaryHospitalVisits.setText("N/A");

            setupBarChart(null);
            setupLineChart(null);
        }
    }

    private void setupBarChart(DashboardAnalytics data) {
        List<BarEntry> entries = new ArrayList<>();
        String[] labels = new String[]{"Overall Adherence"};

        if (hasLinkedUser && data != null && data.getTotalMedicines() > 0) {
            entries.add(new BarEntry(0, data.getMedicineCompliancePercent()));
            BarDataSet dataSet = new BarDataSet(entries, "Overall Adherence");
            dataSet.setColor(Color.parseColor("#4AA5F8"));
            dataSet.setDrawValues(true);
            dataSet.setValueTextColor(Color.WHITE);
            dataSet.setValueTextSize(10f);
            BarData barData = new BarData(dataSet);
            barData.setBarWidth(0.6f);
            barChartAdherence.setData(barData);
        } else {
            entries.add(new BarEntry(0, 0f));
            BarDataSet dataSet = new BarDataSet(entries, "Overall Adherence");
            dataSet.setColor(Color.parseColor("#E0E0E0"));
            dataSet.setDrawValues(true);
            dataSet.setValueTextColor(Color.WHITE);
            dataSet.setValueTextSize(10f);
            BarData barData = new BarData(dataSet);
            barData.setBarWidth(0.6f);
            barChartAdherence.setData(barData);
        }
        
        barChartAdherence.setFitBars(true);
        barChartAdherence.getDescription().setEnabled(false);
        barChartAdherence.getLegend().setEnabled(false);

        XAxis xAxis = barChartAdherence.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.WHITE);

        barChartAdherence.getAxisRight().setEnabled(false);
        barChartAdherence.getAxisLeft().setAxisMinimum(0f);
        barChartAdherence.getAxisLeft().setAxisMaximum(100f);
        barChartAdherence.getAxisLeft().setDrawGridLines(true);
        barChartAdherence.getAxisLeft().enableGridDashedLine(10f, 10f, 0f);
        barChartAdherence.getAxisLeft().setTextColor(Color.WHITE);

        barChartAdherence.invalidate();
    }

    private void setupLineChart(DashboardAnalytics data) {
        List<Entry> entries = new ArrayList<>();
        // Since DashboardAnalytics does not provide daily activity data, we display an empty chart
        // or a placeholder.
        // If you integrate a backend that provides this data, update this section.

        LineDataSet dataSet = new LineDataSet(entries, "Activity");
        dataSet.setColor(Color.parseColor("#E0E0E0"));
        dataSet.setCircleColor(Color.parseColor("#E0E0E0"));
        dataSet.setFillColor(Color.parseColor("#40E0E0E0"));
        dataSet.setLineWidth(3f);
        dataSet.setCircleRadius(5f);
        dataSet.setDrawCircleHole(true);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); 
        dataSet.setDrawFilled(true);

        LineData lineData = new LineData(dataSet);
        lineChartActivity.setData(lineData);
        lineChartActivity.getDescription().setEnabled(false);
        lineChartActivity.getLegend().setEnabled(false);
        
        String[] times = new String[]{"N/A", "N/A", "N/A", "N/A", "N/A"}; // Placeholder labels
        XAxis xAxis = lineChartActivity.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(times));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.WHITE);

        lineChartActivity.getAxisRight().setEnabled(false);
        lineChartActivity.getAxisLeft().setAxisMinimum(0f);
        lineChartActivity.getAxisLeft().setDrawGridLines(true);
        lineChartActivity.getAxisLeft().enableGridDashedLine(10f, 10f, 0f);
        lineChartActivity.getAxisLeft().setTextColor(Color.WHITE);

        lineChartActivity.invalidate();
    }
}
