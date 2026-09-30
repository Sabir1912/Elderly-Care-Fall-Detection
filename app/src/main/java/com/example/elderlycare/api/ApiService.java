package com.example.elderlycare.api;

import com.example.elderlycare.models.User;
import com.example.elderlycare.api.models.LocationPayload;
import com.example.elderlycare.api.models.SpO2Payload;
import com.example.elderlycare.api.models.FallEventPayload;
import com.example.elderlycare.api.models.PrescriptionPayload;
import com.example.elderlycare.api.models.ApiResponse;
import com.example.elderlycare.api.models.DashboardAnalytics;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    @POST("api/auth/login")
    Call<ApiResponse<User>> loginUser(@Body User credentials);

    @POST("api/auth/signup")
    Call<ApiResponse<User>> signupUser(@Body User user);

    /**
     * Kafka Ingestion Streams for the Data Engineering Pipeline.
     */
    @POST("api/stream/location_stream")
    Call<Void> sendLocationUpdate(@Header("Authorization") String token, @Body LocationPayload payload);

    @POST("api/stream/spo2_readings")
    Call<Void> sendSpO2Reading(@Header("Authorization") String token, @Body SpO2Payload payload);

    @POST("api/stream/fall_events")
    Call<Void> sendFallEvent(@Header("Authorization") String token, @Body FallEventPayload payload);

    @POST("api/stream/prescription_logs")
    Call<Void> sendPrescriptionLogs(@Header("Authorization") String token, @Body PrescriptionPayload payload);

    /**
     * Transient End-to-End Relay (No Cloud Storage)
     */
    @POST("api/stream/live_location")
    Call<Void> sendLiveLocation(@Header("Authorization") String token, @Body LocationPayload payload);

    @GET("api/stream/live_location")
    Call<ApiResponse<LocationPayload>> getLiveLocation(@Header("Authorization") String token, @Query("userId") int userId);

    /**
     * Analytics API retrieved from Data Warehouse (BigQuery, Redshift, etc.)
     */
    @GET("api/analytics/guardian_dashboard")
    Call<ApiResponse<DashboardAnalytics>> getGuardianDashboardData(@Header("Authorization") String token, @Query("userId") int userId);

    @GET("api/analytics/medicines")
    Call<ApiResponse<java.util.List<com.example.elderlycare.models.Medicine>>> getMedicines(@Header("Authorization") String token, @Query("userId") int userId);

    @GET("api/users/emergency_contacts")
    Call<ApiResponse<java.util.List<com.example.elderlycare.api.models.EmergencyContact>>> getEmergencyContacts(@Header("Authorization") String token, @Query("userId") int userId);

    @POST("api/users/emergency_contacts")
    Call<ApiResponse<com.example.elderlycare.api.models.EmergencyContact>> addEmergencyContact(@Header("Authorization") String token, @Body com.example.elderlycare.api.models.EmergencyContact contact);

    @retrofit2.http.DELETE("api/users/emergency_contacts/{id}")
    Call<Void> deleteEmergencyContact(@Header("Authorization") String token, @retrofit2.http.Path("id") int contactId);

    @GET("api/users/geofences")
    Call<ApiResponse<java.util.List<com.example.elderlycare.models.GeofenceSettings>>> getGeofences(@Header("Authorization") String token, @Query("userId") int userId);

    @POST("api/users/geofences")
    Call<ApiResponse<com.example.elderlycare.models.GeofenceSettings>> saveGeofence(@Header("Authorization") String token, @Body com.example.elderlycare.models.GeofenceSettings geofence);
}
