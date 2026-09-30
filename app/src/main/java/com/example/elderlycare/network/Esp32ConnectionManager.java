package com.example.elderlycare.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Esp32ConnectionManager implements ConnectionListener {
    private static final String TAG = "Esp32ConnectionManager";
    private final BluetoothHelper bluetoothHelper;
    private WebSocketHelper webSocketHelper;
    private final ConnectionListener uiListener;
    private final String elderlyUid;
    private final FirebaseFirestore firestore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isWifiConnected = false;
    private boolean isBluetoothConnected = false;
    private String manualIp = "";

    public Esp32ConnectionManager(Context context, String elderlyUid, ConnectionListener uiListener) {
        this.elderlyUid = elderlyUid;
        this.uiListener = uiListener;
        this.firestore = FirebaseFirestore.getInstance();
        this.bluetoothHelper = new BluetoothHelper("Minder", this);
    }

    public void setManualIp(String ip) {
        this.manualIp = ip;
    }

    public void connectBluetooth() {
        new Thread(() -> {
            Log.d(TAG, "Attempting BT connection to Minder...");
            bluetoothHelper.connect();
        }).start();
    }

    public boolean isWifiConnected() {
        return isWifiConnected;
    }

    public void connectWifi(String ip) {
        // Sanitize IP: remove http, spaces, and trailing colons
        String cleanIp = ip.replace("http://", "").replace("https://", "").split(":")[0].trim();
        
        if (cleanIp.equals(this.manualIp) && isWifiConnected && webSocketHelper != null) {
            Log.d(TAG, "Already connected to WiFi IP: " + cleanIp);
            return;
        }
        
        this.manualIp = cleanIp;
        if (webSocketHelper != null) {
            webSocketHelper.close();
        }
        

        
        Log.d(TAG, "Initiating WiFi connection to: " + cleanIp);
        webSocketHelper = new WebSocketHelper(cleanIp, this);
        webSocketHelper.connect();
    }

    public void sendMessage(String msg) {
        if (isWifiConnected && webSocketHelper != null) {
            webSocketHelper.send(msg);
        } else if (isBluetoothConnected && bluetoothHelper != null) {
            bluetoothHelper.send(msg);
        }
    }

    private void updateDeviceStatusInCloud() {
        boolean overallConnected = isWifiConnected || isBluetoothConnected;
        if (elderlyUid == null) return;
        
        Map<String, Object> update = new HashMap<>();
        update.put("isMinderConnected", overallConnected ? 1 : 0);
        update.put("lastStatusUpdate", System.currentTimeMillis());
        
        firestore.collection("users").document(elderlyUid)
                .update(update)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Sync: Device is " + (overallConnected ? "ONLINE" : "OFFLINE")))
                .addOnFailureListener(e -> Log.e(TAG, "Firestore sync failed", e));
    }

    @Override
    public void onFallDetected(float confidence) {
        mainHandler.post(() -> uiListener.onFallDetected(confidence));
    }

    @Override
    public void onSosReceived() {
        mainHandler.post(uiListener::onSosReceived);
    }

    @Override
    public void onStatusChanged(String state) {
        mainHandler.post(() -> uiListener.onStatusChanged(state));
    }

    @Override
    public void onHeartbeat() {
        mainHandler.post(uiListener::onHeartbeat);
    }

    @Override
    public void onWifiStateChanged(boolean connected) {
        this.isWifiConnected = connected;
        updateDeviceStatusInCloud();
        mainHandler.post(() -> uiListener.onWifiStateChanged(connected));
    }

    @Override
    public void onBluetoothStateChanged(boolean connected) {
        this.isBluetoothConnected = connected;
        updateDeviceStatusInCloud();
        mainHandler.post(() -> uiListener.onBluetoothStateChanged(connected));
    }

    @Override
    public void onMessageLog(String message) {
        parseMessage(message);
        mainHandler.post(() -> uiListener.onMessageLog(message));
    }

    private void parseMessage(String raw) {
        String msg = raw.replace("[BT] ", "").replace("[WS] RX: ", "").trim();
        if (msg.startsWith("FALL:")) {
            try {
                float conf = Float.parseFloat(msg.substring(5));
                onFallDetected(conf);
            } catch (Exception ignored) {}
        } else if (msg.equals("SOS")) {
            onSosReceived();
        } else if (msg.startsWith("STATUS:")) {
            onStatusChanged(msg.substring(7));
        } else if (msg.equals("HEARTBEAT")) {
            onHeartbeat();
        } else if (msg.startsWith("IP:")) {
            String deviceReportedIp = msg.substring(3);
            Log.d(TAG, "Device reports its IP as: " + deviceReportedIp);
            // Disabled auto-connect here to prevent overriding manual user input.
        }
    }

    public void disconnect() {
        bluetoothHelper.close();
        if (webSocketHelper != null) webSocketHelper.close();
        isWifiConnected = false;
        isBluetoothConnected = false;
        updateDeviceStatusInCloud();
    }
}
