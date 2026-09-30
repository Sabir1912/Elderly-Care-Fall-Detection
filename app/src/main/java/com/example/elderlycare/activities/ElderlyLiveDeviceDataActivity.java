package com.example.elderlycare.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.elderlycare.R;
import com.google.android.material.button.MaterialButton;

public class ElderlyLiveDeviceDataActivity extends AppCompatActivity {

    public static final String ACTION_LIVE_DATA = "com.example.elderlycare.LIVE_DATA";
    public static final String EXTRA_ACC_DATA = "EXTRA_ACC_DATA";
    public static final String EXTRA_GYRO_DATA = "EXTRA_GYRO_DATA";
    public static final String EXTRA_BPM_DATA = "EXTRA_BPM_DATA";

    private TextView tvAccelerometer;
    private TextView tvGyroscope;
    private TextView tvBPM;
    private MaterialButton btnClose;

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_LIVE_DATA.equals(intent.getAction())) {
                if (intent.hasExtra(EXTRA_ACC_DATA)) {
                    tvAccelerometer.setText("Accelerometer:\n" + intent.getStringExtra(EXTRA_ACC_DATA));
                }
                if (intent.hasExtra(EXTRA_GYRO_DATA)) {
                    tvGyroscope.setText("Gyroscope:\n" + intent.getStringExtra(EXTRA_GYRO_DATA));
                }
                if (intent.hasExtra(EXTRA_BPM_DATA)) {
                    tvBPM.setText("BPM:\n" + intent.getStringExtra(EXTRA_BPM_DATA));
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_elderly_live_data);

        tvAccelerometer = findViewById(R.id.tvAccelerometer);
        tvGyroscope = findViewById(R.id.tvGyroscope);
        tvBPM = findViewById(R.id.tvBPM);
        btnClose = findViewById(R.id.btnCloseLiveData);

        btnClose.setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(dataReceiver, new IntentFilter(ACTION_LIVE_DATA));
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataReceiver);
    }
}
