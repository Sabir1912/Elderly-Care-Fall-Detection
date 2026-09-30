package com.example.elderlycare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.elderlycare.database.DatabaseHelper;

import java.util.Objects;

public class LoginActivity extends AppCompatActivity {

    private String role;
    private SessionManager sessionManager;
    private DatabaseHelper dbHelper;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private static final String TAG = "LoginActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        role = getIntent().getStringExtra("ROLE");
        if (role == null) {
            Log.e(TAG, "Role is NULL. Redirecting to RoleSelection.");
            startActivity(new Intent(this, RoleSelectionActivity.class));
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tvLoginTitle);
        tvTitle.setText(getString(R.string.login_title, role));

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sessionManager = new SessionManager(this);
        dbHelper = new DatabaseHelper(this);

        TextInputLayout layLoginId = findViewById(R.id.layLoginId);
        TextInputEditText etLoginId = findViewById(R.id.etLoginId);
        TextInputEditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvGoToSignup = findViewById(R.id.tvGoToSignup);

        layLoginId.setHint("Email Address");

        // Prepare Google Sign-In options
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        SignInButton btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnGoogleSignIn.setOnClickListener(v -> mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        }));

        btnLogin.setOnClickListener(v -> {
            String loginId = Objects.requireNonNull(etLoginId.getText()).toString().trim();
            String password = Objects.requireNonNull(etPassword.getText()).toString().trim();

            if (loginId.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Network Request to Firebase Auth
            Log.d(TAG, "Attempting Email login for: " + loginId);
            mAuth.signInWithEmailAndPassword(loginId, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                Log.d(TAG, "Email login success. UID: " + user.getUid());
                                checkUserRoleInFirestore(user.getUid());
                            } else {
                                Log.e(TAG, "Login successful but current user is null!");
                                Toast.makeText(getApplicationContext(), "Error: User State Null", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            String errorMsg = (task.getException() != null) ? task.getException().getMessage() : "Authentication failed.";
                            Toast.makeText(getApplicationContext(), errorMsg, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        tvGoToSignup.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            intent.putExtra("ROLE", role);
            startActivity(intent);
        });
    }

    private void checkUserRoleInFirestore(String uid) {
        if (uid == null) {
            Log.e(TAG, "checkUserRole: UID is null");
            return;
        }
        
        Log.d(TAG, "Fetching role from Firestore for UID: " + uid);
        db.collection("users").document(uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    String dbRole = document.getString("role");
                    Log.d(TAG, "Role found in cloud: " + dbRole + " | Selected: " + role);
                    
                    if (role != null && role.equals(dbRole)) {
                        // Role matches, proceed to dashboard
                        Long guardianApprovedLong = document.getLong("guardianApproved");
                        int guardianApproved = (guardianApprovedLong != null) ? guardianApprovedLong.intValue() : 0;
                        
                        if ("Elderly".equals(role) && guardianApproved == 0) {
                            Toast.makeText(getApplicationContext(), "Waiting for Guardian Approval...", Toast.LENGTH_LONG).show();
                        }
                        
                        String name = document.getString("name");
                        String username = document.getString("username");
                        String email = document.getString("email");
                        String phone = document.getString("phone");
                        String guardianUsername = document.getString("guardianUsername");
                        Long isMinderConnectedLong = document.getLong("isMinderConnected");
                        int isMinderConnected = (isMinderConnectedLong != null) ? isMinderConnectedLong.intValue() : 0;

                        // Local Sync (Update SQLite)
                        User user = new User();
                        user.setRole(dbRole);
                        user.setName(name);
                        user.setUsername(username);
                        user.setEmail(email);
                        user.setPhone(phone);
                        user.setGuardianUsername(guardianUsername);
                        user.setGuardianApproved(guardianApproved);
                        user.setIsMinderConnected(isMinderConnected);
                        int localId = dbHelper.addOrUpdateUser(user);

                        String displayName = ("Guardian".equals(role)) ? username : name;

                        Log.d(TAG, "Sync complete. Starting Session.");
                        sessionManager.createLoginSession(localId, role, uid, displayName, phone);
                        
                        Intent intent;
                        if ("Guardian".equals(role)) {
                            intent = new Intent(this, GuardianDashboardActivity.class);
                        } else {
                            intent = new Intent(this, ElderlyDashboardActivity.class);
                        }
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        mAuth.signOut();
                        String roleMsg = (dbRole != null) ? "You registered as " + dbRole : "Profile incomplete (role missing).";
                        Toast.makeText(getApplicationContext(), "Role mismatch. " + roleMsg, Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.e(TAG, "Document does not exist in Firestore for UID: " + uid);
                    Toast.makeText(getApplicationContext(), "User profile not found in cloud.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Failed to fetch user data from Firestore", task.getException());
                Toast.makeText(getApplicationContext(), "Connectivity error. Failed to fetch user data.", Toast.LENGTH_SHORT).show();
            }
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
            Toast.makeText(this, "Google Sign In Failed (Error " + e.getStatusCode() + ")", Toast.LENGTH_LONG).show();
            if (e.getStatusCode() == 10) {
                Log.e(TAG, "Status code 10: SHA-1 is not registered in Firebase or google-services.json missing Web Client ID.");
                Toast.makeText(this, "Configuration Error: Missing Web Client ID. Please re-download google-services.json after enabling Google Sign-In in Firebase.", Toast.LENGTH_LONG).show();
            }
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
                            Log.d(TAG, "Google Auth success. UID: " + user.getUid());
                            // Check if they exist in Firestore
                            db.collection("users").document(user.getUid()).get()
                                .addOnCompleteListener(dbTask -> {
                                    if (dbTask.isSuccessful() && dbTask.getResult() != null && dbTask.getResult().exists()) {
                                        Log.d(TAG, "Profile found for Google user. Syncing...");
                                        checkUserRoleInFirestore(user.getUid());
                                    } else {
                                        Log.w(TAG, "Google user profile missing in Firestore.");
                                        Toast.makeText(getApplicationContext(), "Profile missing. Please complete Signup.", Toast.LENGTH_LONG).show();
                                        Intent intent = new Intent(this, SignupActivity.class);
                                        intent.putExtra("ROLE", role);
                                        intent.putExtra("GOOGLE_EMAIL", user.getEmail());
                                        intent.putExtra("GOOGLE_NAME", user.getDisplayName());
                                        startActivity(intent);
                                    }
                                });
                        }
                    } else {
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(getApplicationContext(), "Cloud Auth failed: " + (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
