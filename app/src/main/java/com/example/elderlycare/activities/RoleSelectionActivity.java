package com.example.elderlycare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.example.elderlycare.R;

public class RoleSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        Button btnGuardian = findViewById(R.id.btnGuardian);
        Button btnUser = findViewById(R.id.btnUser);

        btnGuardian.setOnClickListener(v -> {
            Intent intent = new Intent(RoleSelectionActivity.this, LoginActivity.class);
            intent.putExtra("ROLE", "Guardian");
            startActivity(intent);
        });

        btnUser.setOnClickListener(v -> {
            Intent intent = new Intent(RoleSelectionActivity.this, LoginActivity.class);
            intent.putExtra("ROLE", "Elderly");
            startActivity(intent);
        });
    }
}
