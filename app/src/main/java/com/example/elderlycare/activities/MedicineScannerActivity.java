package com.example.elderlycare.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.elderlycare.R;
import com.example.elderlycare.database.DatabaseHelper;
import com.example.elderlycare.models.Medicine;
import com.example.elderlycare.utils.SessionManager;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.widget.EditText;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.elderlycare.api.ApiClient;
import com.example.elderlycare.api.ApiService;
import com.example.elderlycare.api.models.PrescriptionPayload;
import com.google.firebase.firestore.FirebaseFirestore;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MedicineScannerActivity extends AppCompatActivity {

    private static final String TAG = "MedicineScannerActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private PreviewView viewFinder;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private FirebaseFirestore firestore;
    private int userId;
    
    private List<String> pendingMedicinesToAdd = new ArrayList<>();
    private List<PrescriptionPayload.Medication> parsedMedications = new ArrayList<>();
    private String lastScannedText = "";

    private TextRecognizer recognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_scanner);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new SessionManager(this);
        firestore = FirebaseFirestore.getInstance();
        userId = sessionManager.getUserId();

        viewFinder = findViewById(R.id.viewFinder);
        Button btnCapture = findViewById(R.id.btnCapture);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        btnCapture.setOnClickListener(v -> takePhotoAndRecognizeText());

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhotoAndRecognizeText() {
        if (imageCapture == null) return;

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                @SuppressWarnings("UnsafeOptInUsageError")
                android.media.Image mediaImage = image.getImage();
                if (mediaImage != null) {
                    InputImage inputImage = InputImage.fromMediaImage(mediaImage, image.getImageInfo().getRotationDegrees());
                    recognizeText(inputImage, image);
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
            }
        });
    }

    private void recognizeText(InputImage image, ImageProxy imageProxy) {
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String text = visionText.getText();
                    Log.d(TAG, "Recognized Text: \n" + text);
                    lastScannedText = text;
                    List<String> foundMeds = searchMedicineDatabase(text);
                    if (foundMeds != null && !foundMeds.isEmpty()) {
                        showConfirmationDialog(foundMeds);
                    } else {
                        Toast.makeText(this, "No medicines found in database. Try a clearer photo.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Text recognition failed", e);
                    Toast.makeText(this, "Failed to scan text", Toast.LENGTH_SHORT).show();
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private List<String> searchMedicineDatabase(String scannedText) {
        List<String> foundList = new ArrayList<>();
        if (scannedText == null || scannedText.isEmpty()) return foundList;
        String lowerText = scannedText.toLowerCase();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("medicine.csv")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length > 0) {
                    String medName = parts[0].trim();
                    if (!medName.isEmpty() && lowerText.contains(medName.toLowerCase())) {
                        if(!foundList.contains(medName)) {
                            foundList.add(medName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading medicine.csv", e);
        }
        return foundList;
    }

    private void showConfirmationDialog(List<String> medicines) {
        boolean[] checkedItems = new boolean[medicines.size()];
        for(int i=0; i<checkedItems.length; i++) checkedItems[i] = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Verify Found Medicines");
        
        String[] items = medicines.toArray(new String[0]);
        builder.setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
        });

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            pendingMedicinesToAdd.clear();
            parsedMedications.clear();
            for(int i=0; i<checkedItems.length; i++) {
                if(checkedItems[i]) {
                    pendingMedicinesToAdd.add(medicines.get(i));
                }
            }
            if(!pendingMedicinesToAdd.isEmpty()) {
                showNextMedicineTimingDialog();
            }
        });
        builder.setNegativeButton("Retake", null);
        builder.show();
    }

    private void showNextMedicineTimingDialog() {
        if (pendingMedicinesToAdd.isEmpty()) {
            Toast.makeText(this, "All medicines added!", Toast.LENGTH_SHORT).show();
            saveToCloudAndBackend();
            return;
        }
        
        String medName = pendingMedicinesToAdd.remove(0);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_medicine_timing, null);
        
        TextView tvTitle = view.findViewById(R.id.tvTimingDialogTitle);
        tvTitle.setText("Timing for " + medName);
        
        CheckBox cbMorning = view.findViewById(R.id.cbMorning);
        CheckBox cbAfternoon = view.findViewById(R.id.cbAfternoon);
        CheckBox cbEvening = view.findViewById(R.id.cbEvening);
        CheckBox cbNight = view.findViewById(R.id.cbNight);

        builder.setView(view);

        builder.setPositiveButton("Next", (dialog, which) -> {
            StringBuilder timingBuilder = new StringBuilder();
            timingBuilder.append(cbMorning.isChecked() ? "1-" : "0-");
            timingBuilder.append(cbAfternoon.isChecked() ? "1-" : "0-");
            timingBuilder.append(cbEvening.isChecked() ? "1-" : "0-");
            timingBuilder.append(cbNight.isChecked() ? "1" : "0");
            
            String timing = timingBuilder.toString();
            
            // Save locally
            ContentValues values = new ContentValues();
            values.put("userId", userId);
            values.put("name", medName);
            values.put("dosage", "1 Pill");
            values.put("timing", timing);
            values.put("status", "Pending");
            dbHelper.getWritableDatabase().insert("medicines", null, values);

            parsedMedications.add(new PrescriptionPayload.Medication(medName, timing));
            showNextMedicineTimingDialog(); 
        });
        builder.setNegativeButton("Skip", (dialog, which) -> showNextMedicineTimingDialog());
        builder.setCancelable(false);
        builder.show();
    }
    
    private void saveToCloudAndBackend() {
        String uid = sessionManager.getFirebaseUid();
        if (uid != null) {
            for (PrescriptionPayload.Medication med : parsedMedications) {
                Map<String, Object> data = new HashMap<>();
                data.put("name", med.name);
                data.put("timing", med.frequency);
                data.put("dosage", "1 Pill");
                data.put("status", "Pending");
                data.put("timestamp", System.currentTimeMillis());
                
                firestore.collection("users").document(uid)
                        .collection("medicines").add(data);
            }
        }
        
        // Also send to Kafka/Backend API
        sendPrescriptionToBackend();
    }
    
    private void sendPrescriptionToBackend() {
        if (parsedMedications.isEmpty()) {
            finish();
            return;
        }
        
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        String token = "Bearer sample_token"; 
        
        PrescriptionPayload payload = new PrescriptionPayload(userId, lastScannedText, parsedMedications, System.currentTimeMillis());
        
        apiService.sendPrescriptionLogs(token, payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                finish();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                finish();
            }
        });
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (recognizer != null) {
            recognizer.close();
        }
    }
}
