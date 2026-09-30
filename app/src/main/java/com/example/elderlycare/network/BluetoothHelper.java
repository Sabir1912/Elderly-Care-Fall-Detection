package com.example.elderlycare.network;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothHelper {
    private static final String TAG = "BluetoothHelper";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final String deviceName;
    private final BluetoothAdapter btAdapter;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final ConnectionListener listener;
    private volatile boolean isRunning = false;

    public BluetoothHelper(String deviceName, ConnectionListener listener) {
        this.deviceName = deviceName;
        this.btAdapter = BluetoothAdapter.getDefaultAdapter();
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    public boolean connect() {
        if (btAdapter == null || !btAdapter.isEnabled()) return false;

        BluetoothDevice device = null;
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        for (BluetoothDevice d : pairedDevices) {
            if (deviceName.equals(d.getName())) {
                device = d;
                break;
            }
        }

        if (device == null) {
            Log.e(TAG, "Device " + deviceName + " not found among bonded devices.");
            return false;
        }

        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            isRunning = true;
            startReadThread();
            listener.onBluetoothStateChanged(true);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "BT Connection failed: " + e.getMessage());
            close();
            return false;
        }
    }

    private void startReadThread() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            StringBuilder sb = new StringBuilder();
            while (isRunning) {
                try {
                    int bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String data = new String(buffer, 0, bytes);
                        sb.append(data);
                        int newlineIndex;
                        while ((nlIndex = sb.indexOf("\n")) != -1) {
                            String message = sb.substring(0, nlIndex).trim();
                            sb.delete(0, nlIndex + 1);
                            if (!message.isEmpty()) {
                                listener.onMessageLog("[BT] " + message);
                                // The manager will handle parsing
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "BT read error: " + e.getMessage());
                    isRunning = false;
                    listener.onBluetoothStateChanged(false);
                }
            }
        }).start();
    }
    
    // Adjusted read thread to pass data to a callback or just log it for parsing by manager
    // Let's refine the read loop to actually call a specific parse method or generic onMessage
    
    public void send(String msg) {
        if (outputStream != null) {
            try {
                outputStream.write(msg.getBytes());
                outputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "BT send error: " + e.getMessage());
            }
        }
    }

    public void close() {
        isRunning = false;
        try {
            if (socket != null) socket.close();
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
        } catch (IOException ignored) {}
        listener.onBluetoothStateChanged(false);
    }
    
    private int nlIndex; // Added to fix variable scope in lambda if needed, but local is better
}
