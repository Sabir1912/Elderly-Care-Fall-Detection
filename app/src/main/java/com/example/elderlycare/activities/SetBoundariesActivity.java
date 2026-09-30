package com.example.elderlycare.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.elderlycare.R;
import com.example.elderlycare.api.ApiClient;
import com.example.elderlycare.api.ApiService;
import com.example.elderlycare.api.models.ApiResponse;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.GeofenceSettings;
import com.example.elderlycare.models.LocationLog;
import com.example.elderlycare.models.User;
import com.example.elderlycare.utils.SessionManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SetBoundariesActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private ImageView btnBack;
    private TextView tvBoundariesSubtitle, tvMapRadiusIndic, tvRadiusValue, tvCurrentZoneCoords;
    private SeekBar sbRadius;
    private MaterialCardView btnSaveBoundaries;

    private GoogleMap mMap;
    private Marker centerMarker;
    private Circle boundaryCircle;
    private LatLng currentCenter;
    private int currentRadiusParams = 500;

    private SessionManager sessionManager;
    private DatabaseHelper dbHelper;
    private User currentUser, elderlyUser;

    private boolean hasLinkedUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_boundaries);

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
        
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvBoundariesSubtitle = findViewById(R.id.tvBoundariesSubtitle);
        tvMapRadiusIndic = findViewById(R.id.tvMapRadiusIndic);
        tvRadiusValue = findViewById(R.id.tvRadiusValue);
        tvCurrentZoneCoords = findViewById(R.id.tvCurrentZoneCoords);
        sbRadius = findViewById(R.id.sbRadius);
        btnSaveBoundaries = findViewById(R.id.btnSaveBoundaries);

        View layoutEmptyState = findViewById(R.id.layoutEmptyState);
        View cardMapContainer = findViewById(R.id.cardMapContainer);
        View cardActiveZones = findViewById(R.id.cardActiveZones);

        if (hasLinkedUser && elderlyUser != null) {
            tvBoundariesSubtitle.setText("Set safe zones for " + elderlyUser.getName());
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
            if (cardMapContainer != null) cardMapContainer.setVisibility(View.VISIBLE);
            if (cardActiveZones != null) cardActiveZones.setVisibility(View.VISIBLE);
            btnSaveBoundaries.setVisibility(View.VISIBLE);
        } else {
            tvBoundariesSubtitle.setText("No User Linked");
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
            if (cardMapContainer != null) cardMapContainer.setVisibility(View.GONE);
            if (cardActiveZones != null) cardActiveZones.setVisibility(View.GONE);
            btnSaveBoundaries.setVisibility(View.GONE);
            
            sbRadius.setEnabled(false);
            btnSaveBoundaries.setEnabled(false);
            btnSaveBoundaries.setAlpha(0.5f);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        sbRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Minimum radius of 50m
                if (progress < 50) {
                    progress = 50;
                    seekBar.setProgress(50);
                }
                currentRadiusParams = progress;
                updateRadiusUI();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSaveBoundaries.setOnClickListener(v -> saveBoundary());
    }

    private void updateRadiusUI() {
        String radStr = "Radius: " + currentRadiusParams + "m";
        tvMapRadiusIndic.setText(radStr);
        tvRadiusValue.setText(currentRadiusParams + "m");
        drawGeofence();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (hasLinkedUser) {
            mMap.setOnMapClickListener(this);

            // Try to load existing geofence or user location
            if (elderlyUser != null) {
                ApiService apiService = ApiClient.getClient().create(ApiService.class);
                String token = "Bearer sample_token"; 
                
                apiService.getGeofences(token, elderlyUser.getId()).enqueue(new Callback<ApiResponse<List<GeofenceSettings>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<List<GeofenceSettings>>> call, Response<ApiResponse<List<GeofenceSettings>>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            List<GeofenceSettings> list = response.body().getData();
                            if (list != null && !list.isEmpty()) {
                                GeofenceSettings gs = list.get(0);
                                currentCenter = new LatLng(gs.getLatitude(), gs.getLongitude());
                                currentRadiusParams = (int) gs.getRadius();
                                sbRadius.setProgress(currentRadiusParams); // Also triggers updateRadiusUI
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentCenter, 14));
                                drawGeofence();
                                updateCoordsText();
                            } else {
                                fallbackToLocationLog();
                            }
                        } else {
                            fallbackToLocationLog();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<List<GeofenceSettings>>> call, Throwable t) {
                        fallbackToLocationLog();
                    }
                });
            } else {
                currentCenter = new LatLng(37.7749, -122.4194);
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentCenter, 14));
                drawGeofence();
                updateCoordsText();
            }
        } else {
            LatLng defaultLoc = new LatLng(0, 0);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 2));
        }
    }
    
    private void fallbackToLocationLog() {
        LocationLog log = dbHelper.getLatestLocationForUser(elderlyUser.getId());
        if (log != null) {
            currentCenter = new LatLng(log.getLatitude(), log.getLongitude());
        } else {
            currentCenter = new LatLng(37.7749, -122.4194); // Default SF
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentCenter, 14));
        drawGeofence();
        updateCoordsText();
    }

    @Override
    public void onMapClick(LatLng latLng) {
        currentCenter = latLng;
        drawGeofence();
        updateCoordsText();
    }

    private void drawGeofence() {
        if (mMap == null || currentCenter == null) return;

        if (centerMarker != null) centerMarker.remove();
        if (boundaryCircle != null) boundaryCircle.remove();

        centerMarker = mMap.addMarker(new MarkerOptions().position(currentCenter).title("Safe Zone Center"));
        
        CircleOptions circleOptions = new CircleOptions()
                .center(currentCenter)
                .radius(currentRadiusParams)
                .strokeColor(Color.parseColor("#4AA5F8"))
                .strokeWidth(5f)
                .fillColor(Color.parseColor("#404AA5F8"));
        
        boundaryCircle = mMap.addCircle(circleOptions);
    }

    private void updateCoordsText() {
        if (currentCenter != null) {
            tvCurrentZoneCoords.setText(String.format(Locale.getDefault(), "Lat: %.4f, Lng: %.4f", currentCenter.latitude, currentCenter.longitude));
        }
    }

    private void saveBoundary() {
        if (elderlyUser == null || currentCenter == null) {
            Toast.makeText(this, "Cannot save boundary. User or location missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        String token = "Bearer sample_token"; 
        GeofenceSettings payload = new GeofenceSettings(0, elderlyUser.getId(), currentCenter.latitude, currentCenter.longitude, currentRadiusParams);

        apiService.saveGeofence(token, payload).enqueue(new Callback<ApiResponse<GeofenceSettings>>() {
            @Override
            public void onResponse(Call<ApiResponse<GeofenceSettings>> call, Response<ApiResponse<GeofenceSettings>> response) {
                if(response.isSuccessful()) {
                    Toast.makeText(SetBoundariesActivity.this, "Safe zone saved in Cloud!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(SetBoundariesActivity.this, "Failed to save safe zone in Cloud.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<GeofenceSettings>> call, Throwable t) {
                Toast.makeText(SetBoundariesActivity.this, "Network error saving zone.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
