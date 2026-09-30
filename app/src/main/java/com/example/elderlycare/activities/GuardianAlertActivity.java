package com.example.elderlycare.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.elderlycare.R;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.User;
import com.example.elderlycare.models.LocationLog;

public class GuardianAlertActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private DatabaseHelper dbHelper;
    private int elderlyUserId;
    private String elderlyPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn on screen and show over lock screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_guardian_alert);

        dbHelper = new DatabaseHelper(this);
        elderlyUserId = getIntent().getIntExtra("elderlyUserId", -1);
        String elderlyUid = getIntent().getStringExtra("elderlyUid");
        String elderlyName = getIntent().getStringExtra("elderlyName");
        String alertType = getIntent().getStringExtra("alertType");
        String alertMessage = getIntent().getStringExtra("alertMessage");
        elderlyPhone = getIntent().getStringExtra("elderlyPhone");

        TextView tvAlertTitle = findViewById(R.id.tvAlertTitle);
        TextView tvAlertMessage = findViewById(R.id.tvAlertMessage);
        TextView tvLocationInfo = findViewById(R.id.tvLocationInfo);
        Button btnCallElderly = findViewById(R.id.btnCallElderly);
        Button btnDismissAlert = findViewById(R.id.btnDismissAlert);

        if (alertType != null) {
            tvAlertTitle.setText("⚠️ " + alertType + " DETECTED ⚠️");
        } else {
            tvAlertTitle.setText("⚠️ EMERGENCY ALERT ⚠️");
        }
        
        if (alertMessage != null && !alertMessage.isEmpty()) {
            tvAlertMessage.setText(alertMessage);
        }

        if (elderlyName != null) {
            tvAlertTitle.setText(tvAlertTitle.getText() + "\n" + elderlyName);
        }

        // Fetch location fallback log
        if (elderlyUserId != -1) {
            LocationLog log = dbHelper.getLatestLocationForUser(elderlyUserId);
            if (log != null) {
                tvLocationInfo.setText(String.format("Location:\n%.5f, %.5f", log.getLatitude(), log.getLongitude()));
            } else {
                tvLocationInfo.setText("Location unknown");
            }
        }

        // If phone is missing from intent, try local DB
        if (elderlyPhone == null && elderlyUserId != -1) {
            User elderly = dbHelper.getUserById(elderlyUserId);
            if (elderly != null) {
                elderlyPhone = elderly.getPhone();
            }
        }

        startAlarms();

        btnCallElderly.setOnClickListener(v -> {
            stopAlarms();
            if (elderlyPhone != null && !elderlyPhone.isEmpty()) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(Intent.ACTION_CALL);
                    intent.setData(Uri.parse("tel:" + elderlyPhone));
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + elderlyPhone));
                    startActivity(intent);
                }
            } else {
                Toast.makeText(this, "No phone number saved for this user.", Toast.LENGTH_SHORT).show();
            }
        });

        btnDismissAlert.setOnClickListener(v -> {
            stopAlarms();
            Toast.makeText(this, "Alert Dismissed", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void startAlarms() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            mediaPlayer = MediaPlayer.create(this, alarmUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 500, 500};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        }
    }

    private void stopAlarms() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAlarms();
    }
}
