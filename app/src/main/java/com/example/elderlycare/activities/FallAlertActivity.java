package com.example.elderlycare.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.elderlycare.R;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.utils.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FallAlertActivity extends AppCompatActivity {

    private CountDownTimer countDownTimer;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private boolean isCancelled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Ensure this shows over lock screen and turns on screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_fall_alert);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new SessionManager(this);
        
        TextView tvTimer = findViewById(R.id.tvTimer);
        Button btnCancelAlert = findViewById(R.id.btnCancelAlert);
        Button btnCallGuardian = findViewById(R.id.btnCallGuardian); // Assuming this ID exists or I should add it

        // Start Alarm Sound
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

        // Start Vibration
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 500, 500};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        }

        btnCancelAlert.setOnClickListener(v -> {
            isCancelled = true;
            stopAlarms();
            updateAlertStatus("FALSE_ALARM");
            Toast.makeText(this, "Alert Cancelled - Marked as False Alarm", Toast.LENGTH_SHORT).show();
            finish();
        });

        if (btnCallGuardian != null) {
            btnCallGuardian.setOnClickListener(v -> {
                isCancelled = true;
                stopAlarms();
                updateAlertStatus("USER_HANDLED");
                
                // Get Guardian Phone from session if possible, or we might need to fetch it
                // For now, try to find a guardian phone in the system
                String guardianPhone = sessionManager.getPhone(); // This is the user's phone, not necessarily the guardian's
                // In a real app, we'd fetch the linked guardian's phone.
                
                Intent intent = new Intent(Intent.ACTION_DIAL);
                // intent.setData(Uri.parse("tel:" + guardianPhone)); 
                startActivity(intent);
                finish();
            });
        }

        countDownTimer = new CountDownTimer(15000, 1000) {
            @Override
            public void onTick(long l) {
                tvTimer.setText(String.valueOf(l / 1000));
            }

            @Override
            public void onFinish() {
                if (!isCancelled) {
                    stopAlarms();
                    sendSosToCloud();
                    Toast.makeText(FallAlertActivity.this, "SOS Sent to Guardian!", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }.start();
    }
    
    private void updateAlertStatus(String status) {
        // Logic to update the last "NEW" alert in SQLite/Cloud
    }

    private void sendSosToCloud() {
        String uid = sessionManager.getFirebaseUid();
        if (uid != null) {
            Map<String, Object> fallAlert = new HashMap<>();
            fallAlert.put("type", "FALL_CONFIRMED");
            fallAlert.put("message", "Fall detected and not cancelled by user!");
            fallAlert.put("timestamp", System.currentTimeMillis());
            fallAlert.put("status", "NEW");
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .collection("alerts").add(fallAlert);
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
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAlarms();
    }
}
