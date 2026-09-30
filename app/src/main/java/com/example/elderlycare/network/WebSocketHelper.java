package com.example.elderlycare.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketHelper {
    private static final String TAG = "WebSocketHelper";
    private final OkHttpClient client;
    private WebSocket webSocket;
    private final ConnectionListener listener;
    private final String url;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public WebSocketHelper(String ip, ConnectionListener listener) {
        // Explicitly ensuring port 81 for WebSocket to match the ESP32 code
        String cleanIp = ip.replace("http://", "").replace("https://", "").replace("ws://", "").split(":")[0].trim();
        this.url = "ws://" + cleanIp + ":81";
        this.listener = listener;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    public void connect() {
        Request request = new Request.Builder().url(url).build();
        Log.d(TAG, "Attempting WebSocket connect to: " + url);
        
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.d(TAG, "WebSocket Opened: " + url);
                mainHandler.post(() -> listener.onWifiStateChanged(true));
                listener.onMessageLog("[WS] Connected to " + url);
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                Log.d(TAG, "WS Received: " + text);
                mainHandler.post(() -> listener.onMessageLog("[WS] RX: " + text));
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.d(TAG, "WebSocket Closed: " + reason);
                mainHandler.post(() -> listener.onWifiStateChanged(false));
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                Log.e(TAG, "WS Failure: " + t.getMessage());
                mainHandler.post(() -> {
                    listener.onWifiStateChanged(false);
                    listener.onMessageLog("[WS] Error: " + t.getMessage());
                });
            }
        });
    }

    public void send(String msg) {
        if (webSocket != null) {
            webSocket.send(msg);
            Log.d(TAG, "WS Sent: " + msg);
        }
    }

    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "User close");
        }
    }
}