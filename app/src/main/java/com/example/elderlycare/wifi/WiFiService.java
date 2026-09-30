package com.example.elderlycare.wifi;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WiFiService {
    private static final String TAG = "WiFiService";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String esp32IpAddress;

    public interface WiFiCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public WiFiService(String ipAddress) {
        this.esp32IpAddress = ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.esp32IpAddress = ipAddress;
    }

    public String getIpAddress() {
        return esp32IpAddress;
    }

    public static String getMobileIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ip = wifiInfo.getIpAddress();
            return String.format("%d.%d.%d.%d",
                    (ip & 0xff),
                    (ip >> 8 & 0xff),
                    (ip >> 16 & 0xff),
                    (ip >> 24 & 0xff));
        }
        return "0.0.0.0";
    }

    public void sendCommand(String endpoint, String method, String jsonPayload, WiFiCallback callback) {
        if (esp32IpAddress == null || esp32IpAddress.isEmpty()) {
            if (callback != null) callback.onError("IP Address not set");
            return;
        }
        executor.execute(() -> {
            try {
                URL url = new URL("http://" + esp32IpAddress + endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(5000); 
                conn.setReadTimeout(5000);

                if (("POST".equals(method) || "PUT".equals(method)) && jsonPayload != null) {
                    conn.setRequestProperty("Content-Type", "application/json; utf-8");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);
                    try(OutputStream os = conn.getOutputStream()) {
                        byte[] input = jsonPayload.getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }
                }

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(response.toString()));
                } else {
                    if (callback != null) mainHandler.post(() -> callback.onError("Server returned code: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "WiFi Error: ", e);
                if (callback != null) mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public void getSpO2Data(WiFiCallback callback) {
        sendCommand("/api/spo2", "GET", null, callback);
    }

    public void triggerBuzzer(WiFiCallback callback) {
        sendCommand("/api/buzzer", "POST", "{\"action\":\"on\"}", callback);
    }

    public void sendMedicineReminder(String medName, WiFiCallback callback) {
        sendCommand("/api/reminder", "POST", "{\"medicine\":\"" + medName + "\"}", callback);
    }
}
