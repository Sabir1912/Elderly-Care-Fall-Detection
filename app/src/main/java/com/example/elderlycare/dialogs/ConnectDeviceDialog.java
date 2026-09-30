package com.example.elderlycare.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.elderlycare.R;
import com.example.elderlycare.network.Esp32HttpHelper;
import com.example.elderlycare.utils.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;

public class ConnectDeviceDialog extends DialogFragment {

    private EditText etManualIp;
    private View btnConnect;
    private View btnClose;
    private SessionManager sessionManager;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        sessionManager = new SessionManager(requireContext());
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_connect_device, null);

        builder.setView(view);
        Dialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        initViews(view);
        setupListeners();

        return dialog;
    }
    
    private void initViews(View view) {
        etManualIp = view.findViewById(R.id.etManualIp);
        btnClose = view.findViewById(R.id.btnClose);
        btnConnect = view.findViewById(R.id.btnConnect);

        if (etManualIp != null) {
            etManualIp.setText(sessionManager.getLastEspIp());
            if (!etManualIp.getText().toString().isEmpty()) {
                etManualIp.setSelection(etManualIp.getText().length());
            }
        }
    }

    private void setupListeners() {
        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());

        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> {
                String ip = etManualIp.getText().toString().trim();
                if (!ip.isEmpty()) {
                    sessionManager.saveLastEspIp(ip);
                    verifyAndSyncStatus(ip);
                } else {
                    Toast.makeText(getContext(), "Please enter an IP address", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Verifies connection using the correct endpoint and updates dashboard status.
     */
    private void verifyAndSyncStatus(String ip) {
        Esp32HttpHelper httpHelper = new Esp32HttpHelper(ip);
        httpHelper.syncStates(new Esp32HttpHelper.StateCallback() {
            @Override
            public void onSuccess(JSONArray states) {
                // Update cloud status so dashboards reflect connection
                updateCloudStatus(1);
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Successfully connected to Minder at " + ip, Toast.LENGTH_SHORT).show();
                        dismiss();
                    });
                }
            }

            @Override
            public void onError(String error) {
                // Optional: Update cloud status to offline if verification fails
                // updateCloudStatus(0);
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Connection failed: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void updateCloudStatus(int status) {
        String uid = sessionManager.getFirebaseUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update("isMinderConnected", status);
        }
    }
}
