package com.example.elderlycare.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import com.example.elderlycare.R;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.User;
import com.example.elderlycare.utils.SessionManager;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FieldValue;

public class GuardianDashboardActivity extends AppCompatActivity {

    private static final String TAG = "GuardianDashboard";
    private SessionManager sessionManager;
    private DatabaseHelper dbHelper;
    private User currentUser;
    private FirebaseFirestore firestoreDb;
    private List<User> elderlyList = new ArrayList<>();
    private List<String> elderlyUids = new ArrayList<>();
    
    private TextView tvGuardianTitle, tvGuardianSubtitle;
    private TextView tvElderlyName, tvLastActive, tvMedicineStatus, tvLocationStatus;
    private ImageView btnNotifications, btnLogout;
    private MaterialCardView cardTrackUser, cardMedicineChecklist, cardSetBoundaries, cardWeeklyReports;
    private LinearLayout bannerConnectionStatus;
    private View layoutEmptyState;
    private MaterialCardView cardStatus;
    private MaterialButton btnLinkUser;
    private com.google.android.material.card.MaterialCardView cardClearAlerts;

    private boolean hasLinkedUser = false;
    private int selectedElderlyIndex = 0;

    private Handler alertPollingHandler;
    private Runnable alertPollingRunnable;
    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;

    private ListenerRegistration locationListener;
    private ListenerRegistration medicineListener;
    private ListenerRegistration elderlyListListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sessionManager = new SessionManager(this);
        dbHelper = new DatabaseHelper(this);
        firestoreDb = FirebaseFirestore.getInstance();
        
        String uid = sessionManager.getFirebaseUid();
        if (uid == null) {
            redirectToLogin();
            return;
        }
        
        setContentView(R.layout.activity_guardian_dashboard);

        checkOverlayPermission();

        // Start High-Priority Alert Monitoring Service
        Intent serviceIntent = new Intent(this, com.example.elderlycare.services.GuardianNotificationService.class);
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent);

        initViews();
        setupListeners();
        
        firestoreDb.collection("users").document(uid).get()
            .addOnSuccessListener(document -> {
                if(document.exists()) {
                    currentUser = new User();
                    currentUser.setName(document.getString("name"));
                    currentUser.setUsername(document.getString("username"));
                    currentUser.setEmail(document.getString("email"));
                    currentUser.setPhone(document.getString("phone"));
                    
                    tvGuardianSubtitle.setText("Welcome back, " + currentUser.getName());
                    fetchElderlyListFromCloud();
                } else {
                    sessionManager.logout();
                    redirectToLogin();
                }
            });
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                new AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("To receive emergency alerts even when the screen is locked or while using other apps, please allow 'Display over other apps'.")
                        .setPositiveButton("Grant", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }
        }
    }

    private void fetchElderlyListFromCloud() {
        if (elderlyListListener != null) elderlyListListener.remove();

        elderlyListListener = firestoreDb.collection("users")
            .whereEqualTo("role", "Elderly")
            .where(Filter.or(
                Filter.equalTo("guardianUsername", currentUser.getUsername()),
                Filter.equalTo("guardianUsername2", currentUser.getUsername())
            ))
            .addSnapshotListener((queryDocumentSnapshots, e) -> {
                if (e != null || queryDocumentSnapshots == null) return;
                
                elderlyList.clear();
                elderlyUids.clear();
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    User elderly = new User();
                    elderly.setId(doc.getId().hashCode());
                    elderly.setName(doc.getString("name"));
                    elderly.setUsername(doc.getString("username"));
                    elderly.setPhone(doc.getString("phone"));
                    elderly.setEmail(doc.getString("email"));
                    
                    String g1 = doc.getString("guardianUsername");
                    Long approved1 = doc.getLong("guardianApproved");
                    Long approved2 = doc.getLong("guardianApproved2");
                    
                    if (currentUser.getUsername().equals(g1)) {
                        elderly.setGuardianApproved(approved1 != null ? approved1.intValue() : 0);
                    } else {
                        elderly.setGuardianApproved(approved2 != null ? approved2.intValue() : 0);
                    }
                    
                    Long minderConnected = doc.getLong("isMinderConnected");
                    elderly.setIsMinderConnected(minderConnected != null ? minderConnected.intValue() : 0);
                    
                    elderlyList.add(elderly);
                    elderlyUids.add(doc.getId());
                }
                
                if (selectedElderlyIndex >= elderlyList.size()) {
                    selectedElderlyIndex = 0;
                }
                
                loadData();
                checkPendingApprovals();
                startAlertPolling();
            });
    }

    private void initViews() {
        tvGuardianTitle = findViewById(R.id.tvGuardianTitle);
        tvGuardianSubtitle = findViewById(R.id.tvGuardianSubtitle);
        tvElderlyName = findViewById(R.id.tvElderlyName);
        tvLastActive = findViewById(R.id.tvLastActive);
        tvMedicineStatus = findViewById(R.id.tvMedicineStatus);
        tvLocationStatus = findViewById(R.id.tvLocationStatus);

        btnNotifications = findViewById(R.id.btnNotifications);
        btnLogout = findViewById(R.id.btnAccount); 

        cardTrackUser = findViewById(R.id.cardTrackUser);
        cardMedicineChecklist = findViewById(R.id.cardMedicineChecklist);
        cardSetBoundaries = findViewById(R.id.cardSetBoundaries);
        cardWeeklyReports = findViewById(R.id.cardWeeklyReports);

        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        bannerConnectionStatus = findViewById(R.id.bannerConnectionStatus);
        cardStatus = findViewById(R.id.cardStatus);
        btnLinkUser = findViewById(R.id.btnLinkUser);
        cardClearAlerts = findViewById(R.id.cardClearAlerts);
    }

    private void setupListeners() {
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> showAccountDetailsDialog());
        }

        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v -> dismissAllAlerts());
        }

        if (btnLinkUser != null) {
            btnLinkUser.setOnClickListener(v -> Toast.makeText(this, "Link User Setup: Ask the elderly user to select you as their guardian.", Toast.LENGTH_LONG).show());
        }

        if (cardClearAlerts != null) {
            cardClearAlerts.setOnClickListener(v -> {
                if (!hasLinkedUser || elderlyUids.isEmpty()) {
                    Toast.makeText(this, "No elderly user linked", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(this)
                    .setTitle("Clear All Alerts")
                    .setMessage("This will dismiss all queued fall alerts. Are you sure?")
                    .setPositiveButton("Clear All", (dialog, which) -> dismissAllAlerts())
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }

        cardTrackUser.setOnClickListener(v -> navigateTo(TrackUserActivity.class));
        cardMedicineChecklist.setOnClickListener(v -> navigateTo(MedicineListActivity.class));
        
        cardSetBoundaries.setOnClickListener(v -> {
            if (hasLinkedUser && !elderlyList.isEmpty()) {
                User primaryElderly = elderlyList.get(selectedElderlyIndex);
                if (primaryElderly.getPhone() != null) {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + primaryElderly.getPhone()));
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Phone number not found.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Please link an elderly user first", Toast.LENGTH_SHORT).show();
            }
        });
        
        cardWeeklyReports.setOnClickListener(v -> navigateTo(WeeklyReportsActivity.class));
    }

    private void navigateTo(Class<?> activityClass) {
        if (!hasLinkedUser || elderlyList.isEmpty()) {
            Toast.makeText(this, "Please link an elderly user first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (selectedElderlyIndex >= elderlyList.size()) selectedElderlyIndex = 0;
        User primaryElderly = elderlyList.get(selectedElderlyIndex);
        String primaryUid = elderlyUids.get(selectedElderlyIndex);

        Intent intent = new Intent(GuardianDashboardActivity.this, activityClass);
        intent.putExtra("hasLinkedUser", hasLinkedUser);
        intent.putExtra("ELDERLY_ID", primaryElderly.getId());
        intent.putExtra("ELDERLY_UID", primaryUid);
        intent.putExtra("ELDERLY_NAME", primaryElderly.getName());
        startActivity(intent);
    }

    private void loadData() {
        if (elderlyList.isEmpty()) {
            hasLinkedUser = false;
            if (cardStatus != null) cardStatus.setVisibility(View.GONE);
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
            if (bannerConnectionStatus != null) bannerConnectionStatus.setVisibility(View.GONE);
            stopRealtimeListeners();
            return;
        }

        hasLinkedUser = true;
        if (cardStatus != null) cardStatus.setVisibility(View.VISIBLE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        
        if (selectedElderlyIndex >= elderlyList.size()) selectedElderlyIndex = 0;

        User primaryElderly = elderlyList.get(selectedElderlyIndex);
        String primaryUid = elderlyUids.get(selectedElderlyIndex);

        if (tvElderlyName != null) tvElderlyName.setText(primaryElderly.getName() + "'s Status");
        
        if (bannerConnectionStatus != null) {
            bannerConnectionStatus.setVisibility(primaryElderly.getIsMinderConnected() == 1 ? View.GONE : View.VISIBLE);
        }

        startRealtimeListeners(primaryUid);
    }

    private void startRealtimeListeners(String elderlyUid) {
        stopRealtimeListeners();

        // 1. Real-time Medicine Report
        medicineListener = firestoreDb.collection("users").document(elderlyUid)
                .collection("medicines")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    
                    int totalSlots = 0;
                    int takenSlots = 0;
                    for (QueryDocumentSnapshot doc : value) {
                        String timing = doc.getString("timing");
                        String status = doc.getString("status");
                        if (timing != null && status != null) {
                            String[] tSlots = timing.split("-");
                            String[] sSlots = status.split("-");
                            for (int i = 0; i < tSlots.length; i++) {
                                if ("1".equals(tSlots[i])) {
                                    totalSlots++;
                                    if (sSlots.length > i && "Taken".equalsIgnoreCase(sSlots[i])) {
                                        takenSlots++;
                                    }
                                }
                            }
                        }
                    }
                    if (tvMedicineStatus != null) {
                        tvMedicineStatus.setText(String.format(Locale.getDefault(), "%d/%d Doses Taken", takenSlots, totalSlots));
                    }
                });

        // 2. Real-time Location Sync (Fixes the "10 mins ago" issue)
        locationListener = firestoreDb.collection("users").document(elderlyUid)
                .collection("live_location").document("current")
                .addSnapshotListener((document, error) -> {
                    if (error != null || document == null || !document.exists()) {
                        if (tvLocationStatus != null) tvLocationStatus.setText("Unknown Location");
                        if (tvLastActive != null) tvLastActive.setText("Last active: Unknown");
                        return;
                    }

                    Double lat = document.getDouble("latitude");
                    Double lon = document.getDouble("longitude");
                    Long timestamp = document.getLong("timestamp");

                    if (lat != null && lon != null) {
                        if (tvLocationStatus != null) {
                            tvLocationStatus.setText(String.format(Locale.getDefault(), "Lat: %.4f\nLon: %.4f", lat, lon));
                        }
                        if (tvLastActive != null && timestamp != null) {
                            tvLastActive.setText("Last active: " + formatTimeAgo(timestamp));
                        }
                    }
                });
    }

    private void stopRealtimeListeners() {
        if (medicineListener != null) {
            medicineListener.remove();
            medicineListener = null;
        }
        if (locationListener != null) {
            locationListener.remove();
            locationListener = null;
        }
    }

    private String formatTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        if (seconds < 60) return "Just now";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + " hour(s) ago";
        return new java.text.SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(new java.util.Date(timestamp));
    }

    private void showAccountDetailsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_account_details);

        TextView tvName = dialog.findViewById(R.id.tvAccountName);
        TextView tvPhone = dialog.findViewById(R.id.tvAccountPhone);
        TextView tvEmail = dialog.findViewById(R.id.tvAccountEmail);
        android.widget.Button btnLogOutDialog = dialog.findViewById(R.id.btnLogoutFromDialog);
        android.widget.Button btnSwitchUser = dialog.findViewById(R.id.btnSwitchUser);
        android.widget.Button btnManageUsers = dialog.findViewById(R.id.btnManageUsers);

        if (currentUser != null) {
            if (tvName != null) tvName.setText(currentUser.getName());
            if (tvPhone != null) tvPhone.setText(currentUser.getPhone());
            if (tvEmail != null) tvEmail.setText(currentUser.getEmail());
            
            if (elderlyList.size() > 1 && btnSwitchUser != null) {
                btnSwitchUser.setVisibility(View.VISIBLE);
                btnSwitchUser.setText("Switch to Next User");
                btnSwitchUser.setOnClickListener(v -> {
                    selectedElderlyIndex = (selectedElderlyIndex + 1) % elderlyList.size();
                    loadData();
                    dialog.dismiss();
                    Toast.makeText(this, "Switched to " + elderlyList.get(selectedElderlyIndex).getName(), Toast.LENGTH_SHORT).show();
                });
            }

            if (btnManageUsers != null) {
                btnManageUsers.setVisibility(elderlyList.isEmpty() ? View.GONE : View.VISIBLE);
                btnManageUsers.setOnClickListener(v -> {
                    dialog.dismiss();
                    showManageUsersDialog();
                });
            }
        }

        if (btnLogOutDialog != null) {
            btnLogOutDialog.setOnClickListener(v -> {
                sessionManager.logout();
                dialog.dismiss();
                redirectToLogin();
            });
        }
        dialog.show();
    }

    private void showManageUsersDialog() {
        String[] userNames = new String[elderlyList.size()];
        for (int i = 0; i < elderlyList.size(); i++) {
            userNames[i] = elderlyList.get(i).getName();
        }

        new AlertDialog.Builder(this)
            .setTitle("Manage Connected Users")
            .setItems(userNames, (dialog, which) -> {
                User targetUser = elderlyList.get(which);
                String targetUid = elderlyUids.get(which);
                
                new AlertDialog.Builder(this)
                    .setTitle("Remove User")
                    .setMessage("Are you sure you want to disconnect from " + targetUser.getName() + "?")
                    .setPositiveButton("Remove", (confirmDialog, confirmWhich) -> {
                        removeElderlyUserAsync(targetUid, targetUser.getName());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void removeElderlyUserAsync(String elderlyUid, String userName) {
        firestoreDb.collection("users").document(elderlyUid).get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    String g1 = document.getString("guardianUsername");
                    String g2 = document.getString("guardianUsername2");
                    
                    java.util.Map<String, Object> updates = new java.util.HashMap<>();
                    if (currentUser.getUsername().equals(g1)) {
                        updates.put("guardianUsername", "");
                        updates.put("guardianApproved", 0);
                    } else if (currentUser.getUsername().equals(g2)) {
                        updates.put("guardianUsername2", "");
                        updates.put("guardianApproved2", 0);
                    }
                    
                    if (!updates.isEmpty()) {
                        firestoreDb.collection("users").document(elderlyUid)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, userName + " removed successfully", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to remove user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                    }
                }
            });
    }

    private void dismissAllAlerts() {
        if (!hasLinkedUser || elderlyUids.isEmpty()) {
            Toast.makeText(this, "No elderly user linked", Toast.LENGTH_SHORT).show();
            return;
        }
        String primaryUid = elderlyUids.get(selectedElderlyIndex);
        firestoreDb.collection("users").document(primaryUid)
            .collection("alerts")
            .whereEqualTo("status", "NEW")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    com.google.firebase.firestore.WriteBatch batch = firestoreDb.batch();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        batch.update(doc.getReference(), "status", "DISMISSED");
                    }
                    batch.commit().addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Cleared " + queryDocumentSnapshots.size() + " queued alert(s).", Toast.LENGTH_SHORT).show()
                    );
                } else {
                    Toast.makeText(this, "No queued alerts to clear.", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void checkPendingApprovals() {
        if (currentUser == null) return;
        for (int i = 0; i < elderlyList.size(); i++) {
            User elderly = elderlyList.get(i);
            String elderlyUid = elderlyUids.get(i);
            if (elderly.getGuardianApproved() == 0) {
                new AlertDialog.Builder(this)
                    .setTitle("Pending Approval")
                    .setMessage(elderly.getName() + " wants to add you as their guardian.")
                    .setPositiveButton("Approve", (dialog, which) -> {
                        firestoreDb.collection("users").document(elderlyUid).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    String g1 = doc.getString("guardianUsername");
                                    if (currentUser.getUsername().equals(g1)) {
                                        doc.getReference().update("guardianApproved", 1);
                                    } else {
                                        doc.getReference().update("guardianApproved2", 1);
                                    }
                                }
                            });
                    })
                    .setNegativeButton("Reject", (dialog, which) -> {
                        // If rejected, remove them immediately
                        removeElderlyUserAsync(elderlyUid, elderly.getName());
                    })
                    .show();
            }
        }
    }

    private void startAlertPolling() {
        if (alertPollingHandler != null) return;
        alertPollingHandler = new Handler(Looper.getMainLooper());
        alertPollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (hasLinkedUser && !elderlyUids.isEmpty()) {
                    if (selectedElderlyIndex < elderlyUids.size()) {
                        String elderlyUid = elderlyUids.get(selectedElderlyIndex);
                        firestoreDb.collection("users").document(elderlyUid)
                                .collection("alerts")
                                .whereEqualTo("status", "NEW")
                                .limit(1)
                                .get()
                                .addOnSuccessListener(queryDocumentSnapshots -> {
                                    if (!queryDocumentSnapshots.isEmpty()) {
                                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                                        String type = doc.getString("type");
                                        String msg = doc.getString("message");
                                        
                                        Intent alertIntent = new Intent(GuardianDashboardActivity.this, GuardianAlertActivity.class);
                                        alertIntent.putExtra("elderlyUserId", elderlyList.get(selectedElderlyIndex).getId());
                                        alertIntent.putExtra("elderlyUid", elderlyUid);
                                        alertIntent.putExtra("elderlyName", elderlyList.get(selectedElderlyIndex).getName());
                                        alertIntent.putExtra("elderlyPhone", elderlyList.get(selectedElderlyIndex).getPhone());
                                        alertIntent.putExtra("alertType", type);
                                        alertIntent.putExtra("alertMessage", msg != null ? msg : type);
                                        alertIntent.putExtra("guardianPhone", currentUser.getPhone());
                                        startActivity(alertIntent);
                                        
                                        doc.getReference().update("status", "PROCESSING");
                                    }
                                });
                    }
                }
                alertPollingHandler.postDelayed(this, 5000);
            }
        };
        alertPollingHandler.post(alertPollingRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (alertPollingHandler != null) alertPollingHandler.removeCallbacks(alertPollingRunnable);
        stopRealtimeListeners();
        if (elderlyListListener != null) {
            elderlyListListener.remove();
        }
    }
}
