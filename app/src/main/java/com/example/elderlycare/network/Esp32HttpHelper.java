package com.example.elderlycare.network;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import androidx.annotation.NonNull;

public class Esp32HttpHelper {
    private static final String TAG = "Esp32HttpHelper";
    private WebSocket webSocket;
    private final String wsUrl;
    private final String httpUrl;
    private final String cleanIp; 
    private final OkHttpClient client;

    public Esp32HttpHelper(String ip) {
        String tempIp = ip.replace("http://", "").replace("https://", "").replace("ws://", "").trim();
        if (tempIp.contains(":")) {
            this.cleanIp = tempIp.substring(0, tempIp.indexOf(":"));
        } else {
            this.cleanIp = tempIp;
        }

        // HTTP Sync on Port 80, WebSocket on Port 81 (Matches updated Arduino config)
        this.httpUrl = "http://" + cleanIp + ":80";
        this.wsUrl = "ws://" + cleanIp + ":81";

        this.client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) 
                .build();
    }

    public interface MessageListener {
        void onMessageReceived(String message);
        void onError(String error);
        void onConnected();
        void onDisconnected();
    }

    public interface StateCallback {
        void onSuccess(JSONArray states);
        void onError(String error);
    }

    public void connect(MessageListener listener) {
        // Safety: close any existing handle before creating a new one
        if (webSocket != null) {
            webSocket.close(1001, "Reconnecting");
            webSocket = null;
        }
        
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.d(TAG, "WebSocket Connected to " + wsUrl);
                if (listener != null) listener.onConnected();
            }
            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                if (listener != null) listener.onMessageReceived(text);
            }
            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                Log.e(TAG, "WebSocket Failure: " + t.getMessage());
                if (listener != null) listener.onError(t.getMessage());
            }
        });
    }

    public void syncStates(StateCallback callback) {
        Request request = new Request.Builder().url(httpUrl + "/states").build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) callback.onError("Offline");
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (res.isSuccessful()) {
                        String body = res.body() != null ? res.body().string() : "[]";
                        try {
                            callback.onSuccess(new JSONArray(body));
                        } catch (JSONException e) {
                            callback.onSuccess(new JSONArray());
                        }
                    } else {
                        // Even if /states is 404, if the server responded, the device is online
                        callback.onSuccess(new JSONArray());
                    }
                }
            }
        });
    }

    public void sendMedicineReminder(String medicineName) {
        if (webSocket != null) webSocket.send("MED:" + medicineName);
    }

    public void sendMedicineReminderHttp(String medicineName) {
        okhttp3.RequestBody body = okhttp3.RequestBody.create("MED:" + medicineName, okhttp3.MediaType.parse("text/plain"));
        Request request = new Request.Builder()
                .url(httpUrl + "/reminder")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to send HTTP reminder", e);
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "HTTP reminder sent successfully");
                }
                response.close();
            }
        });
    }

    public void sendCancel() {
        if (webSocket != null) webSocket.send("CANCEL");
    }

    public void disconnect() {
        if (webSocket != null) webSocket.close(1000, "User Disconnect");
    }
}