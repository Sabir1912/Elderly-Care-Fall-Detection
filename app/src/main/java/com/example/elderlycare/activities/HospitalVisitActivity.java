package com.example.elderlycare.activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.elderlycare.R;
import com.example.elderlycare.adapters.HospitalVisitAdapter;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.HospitalVisit;
import com.example.elderlycare.utils.SessionManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import com.example.elderlycare.api.ApiClient;
import com.example.elderlycare.api.ApiService;
// Note: We might need a new model for HospitalVisits, or just log it generic for now
import com.example.elderlycare.api.models.ApiResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HospitalVisitActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private RecyclerView rvHospitalVisits;
    private View layoutEmptyState;
    private FloatingActionButton fabAddVisit;
    private ImageView btnBack;
    
    private HospitalVisitAdapter adapter;
    private List<HospitalVisit> visitList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hospital_visit);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new SessionManager(this);

        initViews();
        setupRecyclerView();
        loadVisits();
        setupListeners();
    }

    private void initViews() {
        rvHospitalVisits = findViewById(R.id.rvHospitalVisits);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        fabAddVisit = findViewById(R.id.fabAddVisit);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupRecyclerView() {
        rvHospitalVisits.setLayoutManager(new LinearLayoutManager(this));
        visitList = new ArrayList<>();
        adapter = new HospitalVisitAdapter(this, visitList);
        rvHospitalVisits.setAdapter(adapter);
    }

    private void loadVisits() {
        int userId = sessionManager.getUserId();
        String uid = sessionManager.getFirebaseUid();
        if (userId == -1 && uid != null) {
            userId = uid.hashCode();
        }
        visitList = dbHelper.getHospitalVisitsForUser(userId);
        
        adapter.setVisits(visitList);

        if (visitList.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            rvHospitalVisits.setVisibility(View.GONE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            rvHospitalVisits.setVisibility(View.VISIBLE);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        fabAddVisit.setOnClickListener(v -> showAddVisitDialog());
    }

    private void showAddVisitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Log Hospital Visit");
        
        final EditText input = new EditText(this);
        input.setHint("e.g. Routine checkup, Eye exam...");
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String desc = input.getText().toString().trim();
            if (desc.isEmpty()) {
                Toast.makeText(this, "Please enter details of the visit", Toast.LENGTH_SHORT).show();
                return;
            }

            int userId = sessionManager.getUserId();
            String uidStr = sessionManager.getFirebaseUid();
            if (userId == -1 && uidStr != null) {
                userId = uidStr.hashCode();
            }
            HospitalVisit visit = new HospitalVisit(0, userId, desc, System.currentTimeMillis());
            
            // NOTE: In a complete migration, we'd add an API endpoint for Hospital Visits
            // e.g. apiService.logHospitalVisit(token, visitPayload). For now, simulate network API
            Toast.makeText(this, "Visit logged to Cloud (Simulated)", Toast.LENGTH_SHORT).show();
            
            // To maintain local UI state for prototype:
            long id = dbHelper.insertHospitalVisit(visit);
            if (id > 0) {
                loadVisits();
            } else {
                Toast.makeText(this, "Failed to update local UI", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
