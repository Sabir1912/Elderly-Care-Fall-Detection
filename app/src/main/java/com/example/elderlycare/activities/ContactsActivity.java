package com.example.elderlycare.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.elderlycare.R;
import com.example.elderlycare.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ContactsActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private String currentUid;
    private FirebaseFirestore db;

    private TextView tvGuardianName1, tvGuardianUsername1, tvGuardianName2, tvGuardianUsername2, tvEmergencyContactsTitle;
    private View cardGuardian2;
    private MaterialButton btnAddGuardian;
    private ImageView btnCallGuardian1, btnCallGuardian2, btnRemoveGuardian2;
    private LinearLayout layoutEmergencyContactsContainer;
    private FloatingActionButton fabAddContact;
    private ImageView btnBack;
    
    private String guardianPhone1, guardianPhone2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        db = FirebaseFirestore.getInstance();
        sessionManager = new SessionManager(this);
        currentUid = sessionManager.getFirebaseUid();

        if (currentUid == null) {
            finish();
            return;
        }

        initViews();
        setupListeners();
        loadGuardiansData();
        loadEmergencyContacts();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvGuardianName1 = findViewById(R.id.tvGuardianName1);
        tvGuardianUsername1 = findViewById(R.id.tvGuardianUsername1);
        btnCallGuardian1 = findViewById(R.id.btnCallGuardian1);
        
        cardGuardian2 = findViewById(R.id.cardGuardian2);
        tvGuardianName2 = findViewById(R.id.tvGuardianName2);
        tvGuardianUsername2 = findViewById(R.id.tvGuardianUsername2);
        btnCallGuardian2 = findViewById(R.id.btnCallGuardian2);
        btnRemoveGuardian2 = findViewById(R.id.btnRemoveGuardian2);
        btnAddGuardian = findViewById(R.id.btnAddGuardian);

        tvEmergencyContactsTitle = findViewById(R.id.tvEmergencyContactsTitle);
        layoutEmergencyContactsContainer = findViewById(R.id.layoutEmergencyContactsContainer);
        fabAddContact = findViewById(R.id.fabAddContact);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        fabAddContact.setOnClickListener(v -> showAddContactDialog());
        btnAddGuardian.setOnClickListener(v -> showAddGuardianDialog());
        
        btnCallGuardian1.setOnClickListener(v -> makeCall(guardianPhone1));
        btnCallGuardian2.setOnClickListener(v -> makeCall(guardianPhone2));
        
        btnRemoveGuardian2.setOnClickListener(v -> removeGuardian2());
    }

    private void makeCall(String phone) {
        if (phone != null && !phone.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phone));
            startActivity(intent);
        } else {
            Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadGuardiansData() {
        db.collection("users").document(currentUid).get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    String g1 = document.getString("guardianUsername");
                    String g2 = document.getString("guardianUsername2");
                    
                    if (g1 != null && !g1.isEmpty()) {
                        fetchGuardianInfo(g1, 1);
                    } else {
                        tvGuardianName1.setText("No primary guardian");
                        tvGuardianUsername1.setText("Required");
                    }
                    
                    if (g2 != null && !g2.isEmpty()) {
                        cardGuardian2.setVisibility(View.VISIBLE);
                        btnAddGuardian.setVisibility(View.GONE);
                        fetchGuardianInfo(g2, 2);
                    } else {
                        cardGuardian2.setVisibility(View.GONE);
                        btnAddGuardian.setVisibility(View.VISIBLE);
                    }
                }
            });
    }

    private void fetchGuardianInfo(String username, int slot) {
        db.collection("users")
            .whereEqualTo("username", username)
            .whereEqualTo("role", "Guardian")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                    String name = doc.getString("name");
                    String phone = doc.getString("phone");
                    if (slot == 1) {
                        tvGuardianName1.setText(name);
                        tvGuardianUsername1.setText("@" + username);
                        guardianPhone1 = phone;
                    } else {
                        tvGuardianName2.setText(name);
                        tvGuardianUsername2.setText("@" + username);
                        guardianPhone2 = phone;
                    }
                } else {
                    if (slot == 1) {
                        tvGuardianName1.setText("Guardian Pending");
                        tvGuardianUsername1.setText("@" + username);
                    } else {
                        tvGuardianName2.setText("Guardian 2 Pending");
                        tvGuardianUsername2.setText("@" + username);
                    }
                }
            });
    }

    private void showAddGuardianDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Second Guardian");
        
        final EditText input = new EditText(this);
        input.setHint("Enter Guardian User ID (Username)");
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String username = input.getText().toString().trim();
            if (!username.isEmpty()) {
                addGuardian2(username);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void addGuardian2(String username) {
        // Verify if user exists first and is a Guardian
        db.collection("users")
            .whereEqualTo("username", username)
            .whereEqualTo("role", "Guardian")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (queryDocumentSnapshots.isEmpty()) {
                    Toast.makeText(this, "Guardian user not found", Toast.LENGTH_SHORT).show();
                } else {
                    // Check if guardian already manages 2 elderly
                    db.collection("users")
                        .whereEqualTo("role", "Elderly")
                        .whereIn("guardianUsername", Arrays.asList(username))
                        .get()
                        .addOnSuccessListener(q1 -> {
                            db.collection("users")
                                .whereEqualTo("role", "Elderly")
                                .whereIn("guardianUsername2", Arrays.asList(username))
                                .get()
                                .addOnSuccessListener(q2 -> {
                                    int totalLinked = q1.size() + q2.size();
                                    if (totalLinked >= 2) {
                                        Toast.makeText(this, "This guardian already manages two elderly users", Toast.LENGTH_LONG).show();
                                    } else {
                                        db.collection("users").document(currentUid)
                                            .update("guardianUsername2", username, "guardianApproved2", 0)
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(this, "Second guardian added successfully", Toast.LENGTH_SHORT).show();
                                                loadGuardiansData();
                                            });
                                    }
                                });
                        });
                }
            });
    }

    private void removeGuardian2() {
        new AlertDialog.Builder(this)
            .setTitle("Remove Guardian")
            .setMessage("Are you sure you want to remove your second guardian?")
            .setPositiveButton("Remove", (dialog, which) -> {
                db.collection("users").document(currentUid)
                    .update("guardianUsername2", null, "guardianApproved2", 0)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Second guardian removed", Toast.LENGTH_SHORT).show();
                        loadGuardiansData();
                    });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void loadEmergencyContacts() {
        layoutEmergencyContactsContainer.removeAllViews();
        db.collection("users").document(currentUid).collection("emergency_contacts")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                int count = 0;
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    count++;
                    String name = doc.getString("name");
                    String phone = doc.getString("phone");
                    addContactViewToContainer(doc.getId(), name, phone);
                }
                tvEmergencyContactsTitle.setText("Emergency Contacts (" + count + ")");
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to load contacts", Toast.LENGTH_SHORT).show();
            });
    }

    private void addContactViewToContainer(String contactId, String name, String phone) {
        View contactView = LayoutInflater.from(this).inflate(R.layout.item_contact, layoutEmergencyContactsContainer, false);
        
        TextView tvContactName = contactView.findViewById(R.id.tvContactName);
        TextView tvContactPhone = contactView.findViewById(R.id.tvContactPhone);
        ImageView btnRemoveContact = contactView.findViewById(R.id.btnRemoveContact);

        tvContactName.setText(name);
        tvContactPhone.setText(phone);

        btnRemoveContact.setOnClickListener(v -> deleteContact(contactId));

        layoutEmergencyContactsContainer.addView(contactView);
    }

    private void deleteContact(String contactId) {
        db.collection("users").document(currentUid).collection("emergency_contacts").document(contactId)
            .delete()
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Contact removed", Toast.LENGTH_SHORT).show();
                loadEmergencyContacts();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to remove contact", Toast.LENGTH_SHORT).show();
            });
    }

    private void showAddContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Emergency Contact");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText etName = new EditText(this);
        etName.setHint("Name");
        layout.addView(etName);

        final EditText etPhone = new EditText(this);
        etPhone.setHint("Phone Number");
        layout.addView(etPhone);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            saveEmergencyContact(name, phone);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveEmergencyContact(String name, String phone) {
        Map<String, Object> contact = new HashMap<>();
        contact.put("name", name);
        contact.put("phone", phone);

        db.collection("users").document(currentUid).collection("emergency_contacts")
            .add(contact)
            .addOnSuccessListener(documentReference -> {
                Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show();
                loadEmergencyContacts();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to add contact", Toast.LENGTH_SHORT).show();
            });
    }
}
