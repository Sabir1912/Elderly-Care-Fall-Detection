package com.example.elderlycare.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

public class FindDeviceReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.example.elderlycare.FIND_DEVICE".equals(intent.getAction())) {
            Toast.makeText(context, "Find Device Triggered!", Toast.LENGTH_LONG).show();
            
            // Sound Alarm
            try {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
                MediaPlayer mediaPlayer = MediaPlayer.create(context, alarmUri);
                mediaPlayer.start();
                
                // Stop after 5 seconds just for demo
                new android.os.Handler().postDelayed(() -> {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                    }
                }, 5000);

            } catch (Exception e) {
                e.printStackTrace();
            }

            // Vibrate
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(5000, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }
}
