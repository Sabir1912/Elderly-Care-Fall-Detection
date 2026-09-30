package com.example.elderlycare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.elderlycare.R;
import com.example.elderlycare.models.User;
import com.example.elderlycare.utils.SessionManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.elderlycare.database.DatabaseHelper;
import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private String role;
    private SessionManager sessionManager;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextInputEditText etName, etEmail;
    private static final String TAG = "SignupActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        role = getIntent().getStringExtra("ROLE");
        if (role == null) {
            Log.e(TAG, "Role is NULL. Redirecting to RoleSelection.");
            startActivity(new Intent(this, RoleSelectionActivity.class));
            finish();
            return;
        }
        
        TextView tvTitle = findViewById(R.id.tvSignupTitle);
        tvTitle.setText("Sign Up as " + role);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sessionManager = new SessionManager(this);

        TextInputLayout layUsername = findViewById(R.id.layUsername);
        TextInputLayout layPhone = findViewById(R.id.layPhone);
        TextInputLayout layEmail = findViewById(R.id.layEmail);
        TextInputLayout layGuardianUsername = findViewById(R.id.layGuardianUsername);

        TextInputEditText etUsername = findViewById(R.id.etUsername);
        TextInputEditText etPhone = findViewById(R.id.etPhone);
        TextInputEditText etPass = findViewById(R.id.etPassword);
        TextInputEditText etGuardianUsername = findViewById(R.id.etGuardianUsername);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);

        // Standard prefill from Intent (for redirects from Login)
        String prefillEmail = getIntent().getStringExtra("GOOGLE_EMAIL");
        String prefillName = getIntent().getStringExtra("GOOGLE_NAME");
        if (prefillEmail != null) {
            etEmail.setText(prefillEmail);
            etEmail.setEnabled(false); 
        }
        if (prefillName != null) {
            etName.setText(prefillName);
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        SignInButton btnGoogleSignUp = findViewById(R.id.btnGoogleSignUp);
        btnGoogleSignUp.setOnClickListener(v -> {
            mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        });
        
        if ("Elderly".equals(role)) {
            layUsername.setVisibility(View.GONE); 
            layPhone.setVisibility(View.VISIBLE);
            layEmail.setVisibility(View.VISIBLE);
            layGuardianUsername.setVisibility(View.VISIBLE); 
        } else {
            layUsername.setVisibility(View.VISIBLE);
            layPhone.setVisibility(View.VISIBLE); 
            layEmail.setVisibility(View.VISIBLE); 
            layGuardianUsername.setVisibility(View.GONE);
        }

        Button btnSignup = findViewById(R.id.btnSignup);

        btnSignup.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPass.getText().toString().trim();
            String guardianUsername = etGuardianUsername.getText().toString().trim();

            FirebaseUser currentUser = mAuth.getCurrentUser();
            boolean isGoogleUser = currentUser != null && email.equalsIgnoreCase(currentUser.getEmail());

            if (name.isEmpty() || (!isGoogleUser && password.isEmpty()) || phone.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if ("Guardian".equals(role) && username.isEmpty()) {
                Toast.makeText(this, "Username is required", Toast.LENGTH_SHORT).show();
                return;
            }

            if ("Elderly".equals(role)) {
                if (guardianUsername.isEmpty()) {
                    Toast.makeText(this, "Guardian Username is required", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                db.collection("users")
                    .whereEqualTo("username", guardianUsername)
                    .whereEqualTo("role", "Guardian")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (querySnapshot.isEmpty()) {
                            Toast.makeText(this, "Guardian User ID not found", Toast.LENGTH_SHORT).show();
                        } else {
                            db.collection("users")
                                .whereEqualTo("role", "Elderly")
                                .whereIn("guardianUsername", java.util.Arrays.asList(guardianUsername))
                                .get()
                                .addOnSuccessListener(elderlyQuery -> {
                                    db.collection("users")
                                        .whereEqualTo("role", "Elderly")
                                        .whereIn("guardianUsername2", java.util.Arrays.asList(guardianUsername))
                                        .get()
                                        .addOnSuccessListener(elderlyQuery2 -> {
                                            int totalLinked = elderlyQuery.size() + elderlyQuery2.size();
                                            if (totalLinked >= 2) {
                                                Toast.makeText(this, "This guardian already manages two elderly users", Toast.LENGTH_LONG).show();
                                            } else {
                                                proceedWithSignup(isGoogleUser, currentUser, name, username, phone, email, password, guardianUsername, 0);
                                            }
                                        });
                                });
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Error validating guardian", Toast.LENGTH_SHORT).show());
            } else {
                proceedWithSignup(isGoogleUser, currentUser, name, username, phone, email, password, "", 1);
            }
        });
    }

    private void proceedWithSignup(boolean isGoogleUser, FirebaseUser currentUser, String name, String username, String phone, String email, String password, String guardianUsername, int guardianApproved) {
        if (isGoogleUser) {
            saveUserToFirestore(currentUser.getUid(), name, username, phone, email, guardianUsername, guardianApproved);
        } else {
            mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserToFirestore(user.getUid(), name, username, phone, email, guardianUsername, guardianApproved);
                        }
                    } else {
                        Toast.makeText(getApplicationContext(), "Auth failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
        }
    }

    private void saveUserToFirestore(String uid, String name, String username, String phone, String email, String guardianUsername, int guardianApproved) {
        if (uid == null) {
            Log.e(TAG, "saveUser: UID is null");
            return;
        }

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("role", role);
        userMap.put("name", name);
        userMap.put("username", username);
        userMap.put("phone", phone);
        userMap.put("email", email);
        userMap.put("guardianUsername", guardianUsername);
        userMap.put("guardianApproved", guardianApproved);
        if ("Elderly".equals(role)) {
            userMap.put("guardianUsername2", "");
            userMap.put("guardianApproved2", 0);
        }

        Log.d(TAG, "Saving profile to Firestore for UID: " + uid);
        db.collection("users").document(uid)
                .set(userMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile saved to cloud. Starting local sync.");
                    Toast.makeText(getApplicationContext(), "Signup Successful", Toast.LENGTH_SHORT).show();
                    
                    sessionManager.createCloudSession(role, uid, "Guardian".equals(role) ? username : name, phone);

                    Intent intent = new Intent(this, "Guardian".equals(role) ? GuardianDashboardActivity.class : ElderlyDashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error writing document to Firestore", e);
                    Toast.makeText(getApplicationContext(), "Failed to save profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                    Intent data = result.getData();
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                    handleSignInResult(task);
                } else {
                    Log.e(TAG, "Google Sign In Result Code: " + result.getResultCode());
                    Toast.makeText(this, "Google Sign In Cancelled", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                String idToken = account.getIdToken();
                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken);
                } else {
                    Toast.makeText(this, "Google Sign In Failed: No ID Token", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (ApiException e) {
            Log.e(TAG, "Google Sign In Error: " + e.getStatusCode());
            Toast.makeText(this, "Google Sign Up Failed (Error " + e.getStatusCode() + ")", Toast.LENGTH_LONG).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        Log.d(TAG, "firebaseAuthWithGoogle starting...");
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Log.d(TAG, "Google Auth success for Signup. UID: " + user.getUid());
                            db.collection("users").document(user.getUid()).get()
                                .addOnCompleteListener(dbTask -> {
                                    if (dbTask.isSuccessful() && dbTask.getResult() != null && dbTask.getResult().exists()) {
                                        Log.d(TAG, "Google Account already exists. Redirecting to login.");
                                        mAuth.signOut();
                                        Toast.makeText(getApplicationContext(), "Account exists. Redirecting to Login...", Toast.LENGTH_LONG).show();
                                        Intent intent = new Intent(this, LoginActivity.class);
                                        intent.putExtra("ROLE", role);
                                        startActivity(intent);
                                        finish();
                                    } else {
                                        Log.d(TAG, "Google Account new. Autofilling name and email.");
                                        // AUTOFILL NAME AND EMAIL HERE
                                        etEmail.setText(user.getEmail());
                                        etEmail.setEnabled(false); // Fix email to Google Account
                                        etName.setText(user.getDisplayName());
                                        Toast.makeText(getApplicationContext(), "Autofilled from Google. Please complete other details.", Toast.LENGTH_LONG).show();
                                    }
                                });
                        }
                    } else {
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            Toast.makeText(getApplicationContext(), "Email already exists with another method. Please log in.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getApplicationContext(), "Cloud Auth failed: " + (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
