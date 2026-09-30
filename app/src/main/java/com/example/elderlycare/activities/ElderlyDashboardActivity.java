package com.example.elderlycare.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.elderlycare.R;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.dialogs.ConnectDeviceDialog;
import com.example.elderlycare.models.User;
import com.example.elderlycare.network.ConnectionListener;
import com.example.elderlycare.network.Esp32ConnectionManager;
import com.example.elderlycare.services.LocationTrackingService;
import com.example.elderlycare.services.BluetoothListenerService;
import com.example.elderlycare.services.DeviceSyncService;
import com.example.elderlycare.utils.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ElderlyDashboardActivity extends AppCompatActivity implements ConnectionListener {

    private SessionManager sessionManager;
    private DatabaseHelper dbHelper;
    private User currentUser;
    private TextView tvHardwareStatus;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int BATTERY_OPTIMIZATION_REQUEST_CODE = 1002;
    
    private String g1Phone, g2Phone;
    private ListenerRegistration medicineListener;
    private ListenerRegistration deviceStatusListener;
    
    private Esp32ConnectionManager connectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        dbHelper = new DatabaseHelper(this);

        String uid = sessionManager.getFirebaseUid();
        if (uid == null) {
            redirectToLogin();
            return;
        }

        currentUser = new User();
        currentUser.setName("Loading...");

        setContentView(R.layout.activity_elderly_dashboard);

        TextView tvDashboardTitle = findViewById(R.id.tvDashboardTitle);
        tvHardwareStatus = findViewById(R.id.tvHardwareStatus);

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(document -> {
                    if(document.exists()) {
                        currentUser.setName(document.getString("name"));
                        currentUser.setEmail(document.getString("email"));
                        currentUser.setPhone(document.getString("phone"));
                        currentUser.setUsername(document.getString("username"));

                        if (tvDashboardTitle != null) {
                            tvDashboardTitle.setText("User: " + (currentUser.getName() != null ? currentUser.getName() : "Unknown"));
                        }
                        
                        loadGuardiansInfo(document.getString("guardianUsername"), document.getString("guardianUsername2"));
                        startMedicineRealtimeUpdates(uid);
                        startDeviceStatusRealtimeUpdates(uid);
                    }
                });

        connectionManager = new Esp32ConnectionManager(this, uid, this);
        
        checkPermissionsAndStartServices();
        requestIgnoreBatteryOptimizations();
        initClickListeners();
    }

    private void startDeviceStatusRealtimeUpdates(String uid) {
        if (deviceStatusListener != null) deviceStatusListener.remove();

        deviceStatusListener = FirebaseFirestore.getInstance().collection("users").document(uid)
                .addSnapshotListener((document, error) -> {
                    if (error != null || document == null || !document.exists()) return;

                    Long isConnected = document.getLong("isMinderConnected");
                    if (tvHardwareStatus != null) {
                        if (isConnected != null && isConnected == 1) {
                            tvHardwareStatus.setText("Device: Online");
                            tvHardwareStatus.setTextColor(Color.parseColor("#4CAF50"));
                            String lastIp = sessionManager.getLastEspIp();
                            if (!lastIp.isEmpty() && !connectionManager.isWifiConnected()) {
                                connectionManager.connectWifi(lastIp);
                            }
                        } else {
                            tvHardwareStatus.setText("Device: Offline");
                            tvHardwareStatus.setTextColor(Color.parseColor("#F44336"));
                        }
                    }
                });
    }
    
    private void startMedicineRealtimeUpdates(String uid) {
        if (medicineListener != null) medicineListener.remove();
        
        medicineListener = FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("medicines")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    
                    int totalSlots = 0, takenSlots = 0;
                    for (QueryDocumentSnapshot doc : value) {
                        String timing = doc.getString("timing");
                        String status = doc.getString("status");
                        if (timing != null && status != null) {
                            String[] tSlots = timing.split(",");
                            String[] sSlots = status.split(",");
                            for (int i = 0; i < tSlots.length; i++) {
                                totalSlots++;
                                if (sSlots.length > i && "Taken".equalsIgnoreCase(sSlots[i])) takenSlots++;
                            }
                        }
                    }
                    
                    TextView tvMedReport = findViewById(R.id.tvMedicineReport);
                    if (tvMedReport != null) {
                        tvMedReport.setText(totalSlots > 0 ? "Medicines: " + takenSlots + "/" + totalSlots + " doses taken" : "No medicines scheduled");
                    }
                });
    }

    private void loadGuardiansInfo(String g1, String g2) {
        TextView tvG1 = findViewById(R.id.tvQuickGuardianName1);
        TextView tvG2 = findViewById(R.id.tvQuickGuardianName2);
        View cardG2 = findViewById(R.id.cardCallGuardian2);
        TextView tvGuardianCount = findViewById(R.id.tvGuardianCount);
        
        if (g1 != null && !g1.isEmpty()) {
            FirebaseFirestore.getInstance().collection("users").whereEqualTo("username", g1).whereEqualTo("role", "Guardian").get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        g1Phone = query.getDocuments().get(0).getString("phone");
                        if (tvG1 != null) tvG1.setText("Call " + query.getDocuments().get(0).getString("name"));
                    }
                });
        }
        
        if (g2 != null && !g2.isEmpty()) {
            if (cardG2 != null) cardG2.setVisibility(View.VISIBLE);
            if (tvGuardianCount != null) tvGuardianCount.setText("2 Guardians");
            FirebaseFirestore.getInstance().collection("users").whereEqualTo("username", g2).whereEqualTo("role", "Guardian").get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        g2Phone = query.getDocuments().get(0).getString("phone");
                        if (tvG2 != null) tvG2.setText("Call " + query.getDocuments().get(0).getString("name"));
                    }
                });
        } else {
            if (cardG2 != null) cardG2.setVisibility(View.GONE);
            if (tvGuardianCount != null) tvGuardianCount.setText("1 Guardian");
        }
    }

    private void checkPermissionsAndStartServices() {
        List<String> permissionsNeeded = new ArrayList<>();
        permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        checkOverlayPermission();

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String perm : permissionsNeeded) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) listPermissionsNeeded.add(perm);
        }

        if (!listPermissionsNeeded.isEmpty()) ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        else startBackgroundServices();
    }

    @SuppressLint("BatteryLife")
    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE);
            }
        }
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("To display fall alerts over the lock screen properly, please allow 'Display over other apps'.")
                        .setPositiveButton("Grant", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, 1234);
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) startBackgroundServices();
    }

    private void startBackgroundServices() {
        startForegroundService(new Intent(this, LocationTrackingService.class));
        startForegroundService(new Intent(this, BluetoothListenerService.class));
        startForegroundService(new Intent(this, DeviceSyncService.class));
    }

    private void makeCall(String phone) {
        if (phone != null && !phone.isEmpty()) startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        else Toast.makeText(this, "Guardian phone number not found", Toast.LENGTH_SHORT).show();
    }

    private void initClickListeners() {
        findViewById(R.id.btnAccount).setOnClickListener(v -> showAccountDetailsDialog());
        findViewById(R.id.cardCallGuardian1).setOnClickListener(v -> makeCall(g1Phone));
        View g2 = findViewById(R.id.cardCallGuardian2);
        if (g2 != null) g2.setOnClickListener(v -> makeCall(g2Phone));

        findViewById(R.id.btnSos).setOnClickListener(v -> {
            if (currentUser != null && sessionManager.getFirebaseUid() != null) {
                java.util.Map<String, Object> sosData = new java.util.HashMap<>();
                sosData.put("type", "SOS");
                sosData.put("message", "Emergency SOS triggered!");
                sosData.put("timestamp", System.currentTimeMillis());
                sosData.put("status", "NEW");
                sosData.put("userName", currentUser.getName());
                sosData.put("userPhone", currentUser.getPhone());

                FirebaseFirestore.getInstance().collection("users").document(sessionManager.getFirebaseUid())
                        .collection("alerts").add(sosData)
                        .addOnSuccessListener(ref -> Toast.makeText(this, "SOS Alert Sent!", Toast.LENGTH_SHORT).show());
            }
        });

        findViewById(R.id.cardMedicine).setOnClickListener(v -> startActivity(new Intent(this, MedicineListActivity.class)));
        findViewById(R.id.cardDeviceStatus).setOnClickListener(v -> new ConnectDeviceDialog().show(getSupportFragmentManager(), "ConnectDeviceDialog"));
        findViewById(R.id.cardHospital).setOnClickListener(v -> startActivity(new Intent(this, HospitalVisitActivity.class)));
        findViewById(R.id.cardContacts).setOnClickListener(v -> startActivity(new Intent(this, ContactsActivity.class)));

        // Test Reminder Click Listener
        findViewById(R.id.cardTestReminder).setOnClickListener(v -> {
            if (connectionManager != null) {
                connectionManager.sendMessage("MED:Test Medicine|1 Pill");
                Toast.makeText(this, "Sending Test Reminder to device...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Device not connected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAccountDetailsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_account_details);
        ((TextView)dialog.findViewById(R.id.tvAccountName)).setText(currentUser.getName());
        ((TextView)dialog.findViewById(R.id.tvAccountPhone)).setText(currentUser.getPhone());
        ((TextView)dialog.findViewById(R.id.tvAccountEmail)).setText(currentUser.getEmail());
        dialog.findViewById(R.id.btnLogoutFromDialog).setOnClickListener(v -> {
            sessionManager.logout();
            dialog.dismiss();
            redirectToLogin();
        });
        dialog.show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ConnectionListener implementation
    @Override public void onFallDetected(float conf) {
        Log.d("ElderlyDashboard", "Fall detected over WiFi! Conf: " + conf);
        Intent fallIntent = new Intent(this, FallAlertActivity.class);
        fallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(fallIntent);

        // Save alert locally
        com.example.elderlycare.models.Alert alert = new com.example.elderlycare.models.Alert(0, sessionManager.getUserId(), "FALL", "Fall detected by Minder device!", System.currentTimeMillis(), "NEW");
        dbHelper.insertAlert(alert);

        if (sessionManager.getFirebaseUid() != null) {
            java.util.Map<String, Object> fallAlert = new java.util.HashMap<>();
            fallAlert.put("type", "FALL");
            fallAlert.put("message", "Fall detected by device!");
            fallAlert.put("timestamp", System.currentTimeMillis());
            fallAlert.put("status", "NEW");
            FirebaseFirestore.getInstance().collection("users").document(sessionManager.getFirebaseUid())
                    .collection("alerts").add(fallAlert);
        }
    }

    @Override public void onSosReceived() {
        if (currentUser != null && sessionManager.getFirebaseUid() != null) {
            java.util.Map<String, Object> sosData = new java.util.HashMap<>();
            sosData.put("type", "SOS");
            sosData.put("message", "Emergency SOS triggered from device button!");
            sosData.put("timestamp", System.currentTimeMillis());
            sosData.put("status", "NEW");
            sosData.put("userName", currentUser.getName());
            sosData.put("userPhone", currentUser.getPhone());

            FirebaseFirestore.getInstance().collection("users").document(sessionManager.getFirebaseUid())
                    .collection("alerts").add(sosData);
            
            runOnUiThread(() -> Toast.makeText(this, "Hardware SOS Received & Sent!", Toast.LENGTH_SHORT).show());
        }
    }
    @Override public void onStatusChanged(String state) {}
    @Override public void onHeartbeat() {}
    @Override public void onWifiStateChanged(boolean connected) {}
    @Override public void onBluetoothStateChanged(boolean connected) {}
    @Override public void onMessageLog(String message) {
        if (message.contains("MED_TAKEN")) {
            runOnUiThread(() -> Toast.makeText(this, "Device Confirmation: Medicine Taken!", Toast.LENGTH_LONG).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (medicineListener != null) medicineListener.remove();
        if (deviceStatusListener != null) deviceStatusListener.remove();
        if (connectionManager != null) connectionManager.disconnect();
    }
}
