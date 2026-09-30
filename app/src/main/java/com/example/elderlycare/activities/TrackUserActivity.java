package com.example.elderlycare.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.elderlycare.R;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.User;
import com.example.elderlycare.network.ConnectionListener;
import com.example.elderlycare.network.Esp32ConnectionManager;
import com.example.elderlycare.network.Esp32HttpHelper;
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
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TrackUserActivity extends AppCompatActivity implements OnMapReadyCallback, ConnectionListener {

    private static final String TAG = "TrackUserActivity";
    private SessionManager sessionManager;
    private DatabaseHelper dbHelper;
    private FirebaseFirestore firestore;
    private User elderlyUser;
    private String elderlyUid;

    private TextView tvTrackUserSubtitle, tvMapUpdatedTime, tvLiveSpeed;
    private TextView tvAddressTitle, tvAddressDetails, tvZoneStatus;
    private ImageView btnBack;
    private MaterialButton btnSetBoundary;
    private RecyclerView rvLocationHistory;
    
    private GoogleMap mMap;
    private Marker elderlyMarker;
    private Circle geofenceCircle;
    private ListenerRegistration locationListenerRegistration;
    private ListenerRegistration locationHistoryListenerRegistration;

    private boolean isSetBoundaryMode = false;
    private boolean hasLinkedUser = false;

    // ESP32 Connection Manager
    private Esp32ConnectionManager connectionManager;

    // UI References for Manual Connection
    private TextView  tvLog;
    private ScrollView scrollLog;
    private TextView  tvWsStatus;
    private EditText  etWsIp;
    private EditText  etMedName;
    private Button    btnWsConnect;
    private Button    btnSendMed;
    private Button    btnCancel;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_user);

        hasLinkedUser = getIntent().getBooleanExtra("hasLinkedUser", false);
        elderlyUid = getIntent().getStringExtra("ELDERLY_UID");

        sessionManager = new SessionManager(this);
        dbHelper = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();

        if (elderlyUid == null) {
            elderlyUid = sessionManager.getFirebaseUid();
        }

        connectionManager = new Esp32ConnectionManager(this, elderlyUid, this);

        initViews();
        setupListeners();
        initEsp32ConnectivityViews(); 
        
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void initViews() {
        tvTrackUserSubtitle = findViewById(R.id.tvTrackUserSubtitle);
        tvMapUpdatedTime = findViewById(R.id.tvMapUpdatedTime);
        tvLiveSpeed = findViewById(R.id.tvLiveSpeed);
        tvAddressTitle = findViewById(R.id.tvAddressTitle);
        tvAddressDetails = findViewById(R.id.tvAddressDetails);
        tvZoneStatus = findViewById(R.id.tvZoneStatus);
        btnSetBoundary = findViewById(R.id.btnSetBoundary);
        
        btnBack = findViewById(R.id.btnBack);
        rvLocationHistory = findViewById(R.id.rvLocationHistory);
        rvLocationHistory.setLayoutManager(new LinearLayoutManager(this));

        View layoutEmptyState = findViewById(R.id.layoutEmptyState);
        View cardMapContainer = findViewById(R.id.cardMapContainer);
        View cardLocationHistory = findViewById(R.id.cardLocationHistory);

        if (hasLinkedUser) {
            tvTrackUserSubtitle.setText("Live tracking enabled");
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
            if (cardMapContainer != null) cardMapContainer.setVisibility(View.VISIBLE);
            if (cardLocationHistory != null) cardLocationHistory.setVisibility(View.VISIBLE);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnSetBoundary.setOnClickListener(v -> toggleBoundaryMode());
    }

    private void toggleBoundaryMode() {
        isSetBoundaryMode = !isSetBoundaryMode;
        btnSetBoundary.setText(isSetBoundaryMode ? "Exit Mode" : "Set Zone");
        if (isSetBoundaryMode) {
            Toast.makeText(this, "Long press on map to set safe zone", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapLongClickListener(latLng -> {
            if (isSetBoundaryMode) {
                setGeofenceOnMap(latLng, 200);
                saveGeofenceToCloud(latLng, 200);
            }
        });

        if (hasLinkedUser) {
            startRealTimeLocationUpdates();
        } else {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0, 0), 2));
        }
    }

    private void setGeofenceOnMap(LatLng latLng, float radius) {
        if (geofenceCircle != null) geofenceCircle.remove();
        geofenceCircle = mMap.addCircle(new CircleOptions()
                .center(latLng)
                .radius(radius)
                .strokeColor(Color.argb(100, 74, 165, 248))
                .fillColor(Color.argb(50, 74, 165, 248))
                .strokeWidth(2));
    }

    private void saveGeofenceToCloud(LatLng latLng, float radius) {
        if (elderlyUid == null) return;
        Map<String, Object> gf = new HashMap<>();
        gf.put("latitude", latLng.latitude);
        gf.put("longitude", latLng.longitude);
        gf.put("radius", radius);
        firestore.collection("users").document(elderlyUid)
                .collection("settings").document("geofence").set(gf);
    }

    private void startRealTimeLocationUpdates() {
        if (elderlyUid == null) return;
        
        locationListenerRegistration = firestore.collection("users").document(elderlyUid)
                .collection("live_location").document("current")
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    Double lat = doc.getDouble("latitude");
                    Double lon = doc.getDouble("longitude");
                    Long timestamp = doc.getLong("timestamp");
                    Float speed = doc.contains("speed") ? doc.getDouble("speed").floatValue() : 0.0f;
                    if (lat != null && lon != null) {
                        updateMap(lat, lon, speed, timestamp);
                    }
                });
        loadLocationHistory();
    }

    private void updateMap(double lat, double lon, float speed, Long timestamp) {
        if (mMap == null) return;
        LatLng loc = new LatLng(lat, lon);
        if (elderlyMarker == null) {
            elderlyMarker = mMap.addMarker(new MarkerOptions().position(loc).title("Elderly User"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, 17));
        } else {
            elderlyMarker.setPosition(loc);
        }
        tvLiveSpeed.setText(String.format(Locale.getDefault(), "%.1f m/s", speed));
        tvMapUpdatedTime.setText("Last Active: " + formatTimeAgo(timestamp));
        updateAddressText(lat, lon);
    }

    private void updateAddressText(double lat, double lon) {
        executor.execute(() -> {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String title = addresses.get(0).getLocality();
                    String details = addresses.get(0).getAddressLine(0);
                    mainHandler.post(() -> {
                        tvAddressTitle.setText(title != null ? title : "Current Area");
                        tvAddressDetails.setText(details);
                    });
                }
            } catch (IOException ignored) {}
        });
    }

    private void loadLocationHistory() {
        if (elderlyUid == null) return;
        locationHistoryListenerRegistration = firestore.collection("users").document(elderlyUid)
                .collection("location_history")
                .orderBy("timestamp", Query.Direction.DESCENDING).limit(10)
                .addSnapshotListener((query, e) -> {
                    if (e != null || query == null) return;
                    List<HistoryItem> items = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : query.getDocuments()) {
                        items.add(new HistoryItem("Visited location", formatTimeAgo(doc.getLong("timestamp"))));
                    }
                    rvLocationHistory.setAdapter(new HistoryAdapter(items));
                });
    }

    private String formatTimeAgo(Long timestamp) {
        if (timestamp == null) return "Unknown";
        long diff = System.currentTimeMillis() - timestamp;
        long mins = TimeUnit.MILLISECONDS.toMinutes(diff);
        if (mins < 1) return "Just now";
        if (mins < 60) return mins + " min ago";
        return TimeUnit.MILLISECONDS.toHours(diff) + " hours ago";
    }

    @Override
    public void onFallDetected(float confidence) {
        toastEsp32("⚠️ FALL DETECTED! Conf: " + confidence + "%");
    }

    @Override
    public void onSosReceived() {
        toastEsp32("🆘 SOS RECEIVED!");
    }

    @Override
    public void onStatusChanged(String state) {
        logEsp32("Device Status: " + state);
    }

    @Override
    public void onHeartbeat() {
        Log.d(TAG, "Heartbeat from Minder");
    }

    @Override
    public void onWifiStateChanged(boolean connected) {
        if (tvWsStatus != null) {
            tvWsStatus.setText("Status: " + (connected ? "Connected" : "Disconnected"));
            tvWsStatus.setTextColor(connected ? 0xFF00AA00 : 0xFFCC0000);
        }
    }

    @Override
    public void onBluetoothStateChanged(boolean connected) {
        // Obsolete
    }

    @Override
    public void onMessageLog(String message) {
        logEsp32(message);
    }

    private void initEsp32ConnectivityViews() {
        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);
        tvWsStatus = findViewById(R.id.tvWsStatus);
        etWsIp = findViewById(R.id.etWsIp);
        etMedName = findViewById(R.id.etMedName);
        btnWsConnect = findViewById(R.id.btnWsConnect);
        btnSendMed = findViewById(R.id.btnSendMed);
        btnCancel = findViewById(R.id.btnCancel);

        if (etWsIp != null) etWsIp.setText(sessionManager.getLastEspIp());

        if (btnWsConnect != null) btnWsConnect.setOnClickListener(v -> onWsConnectClick());
        if (btnSendMed != null) btnSendMed.setOnClickListener(v -> connectionManager.sendMessage("MED:" + etMedName.getText().toString()));
        if (btnCancel != null) btnCancel.setOnClickListener(v -> connectionManager.sendMessage("CANCEL"));
    }

    private void onWsConnectClick() {
        String ip = etWsIp.getText().toString().trim();
        if (ip.isEmpty()) { toastEsp32("Enter ESP32 IP address"); return; }
        
        sessionManager.saveLastEspIp(ip);
        logEsp32("Connecting to: " + ip);
        
        Esp32HttpHelper httpHelper = new Esp32HttpHelper(ip);
        httpHelper.syncStates(new Esp32HttpHelper.StateCallback() {
            @Override
            public void onSuccess(JSONArray states) {
                logEsp32("HTTP Sync Success. Establishing WebSocket...");
                mainHandler.post(() -> connectionManager.connectWifi(ip));
            }
            @Override
            public void onError(String error) {
                logEsp32("HTTP Sync Warning: " + error + ". Trying WebSocket anyway.");
                mainHandler.post(() -> connectionManager.connectWifi(ip));
            }
        });
    }

    private void logEsp32(String msg) {
        runOnUiThread(() -> {
            if (tvLog != null) {
                tvLog.append(msg + "\n");
                scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    private void toastEsp32(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationListenerRegistration != null) locationListenerRegistration.remove();
        if (locationHistoryListenerRegistration != null) locationHistoryListenerRegistration.remove();
        executor.shutdownNow();
    }

    static class HistoryItem {
        String address, time;
        HistoryItem(String a, String t) { this.address = a; this.time = t; }
    }

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<HistoryItem> items;
        HistoryAdapter(List<HistoryItem> i) { this.items = i; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_location_history, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            h.tvAddress.setText(items.get(p).address);
            h.tvTime.setText(items.get(p).time);
        }
        @Override public int getItemCount() { return items.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvAddress, tvTime;
            ViewHolder(View v) { super(v); tvAddress = v.findViewById(R.id.tvHistoryAddress); tvTime = v.findViewById(R.id.tvHistoryTime); }
        }
    }
}
