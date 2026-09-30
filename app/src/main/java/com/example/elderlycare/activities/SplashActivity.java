package com.example.elderlycare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

import com.example.elderlycare.R;
import com.example.elderlycare.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(() -> {
            SessionManager sessionManager = new SessionManager(this);
            if (sessionManager.isLoggedIn()) {
                String role = sessionManager.getRole();
                if ("Guardian".equals(role)) {
                    startActivity(new Intent(SplashActivity.this, GuardianDashboardActivity.class));
                } else {
                    startActivity(new Intent(SplashActivity.this, ElderlyDashboardActivity.class));
                }
            } else {
                startActivity(new Intent(SplashActivity.this, RoleSelectionActivity.class));
            }
            finish();
        }, 2000);
    }
}
