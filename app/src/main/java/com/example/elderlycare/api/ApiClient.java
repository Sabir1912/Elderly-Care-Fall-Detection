package com.example.elderlycare.api;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    // This is used for standard cloud backend if needed
    private static final String CLOUD_BASE_URL = "https://api.elderlycare.mock-backend.com/"; 
    private static Retrofit cloudRetrofit = null;

    public static Retrofit getClient() {
        if (cloudRetrofit == null) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .build();

            cloudRetrofit = new Retrofit.Builder()
                    .baseUrl(CLOUD_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return cloudRetrofit;
    }

    /**
     * Optimized for ESP32 Manual IP connection.
     * This method ensures NO static IP is reused. It creates a fresh service 
     * for the specific IP entered by the user.
     */
    public static ApiService getEsp32Service(String ip) {
        // Sanitize IP: ensure no http:// prefix and no trailing slashes or colons
        String cleanIp = ip.replace("http://", "").replace("https://", "").trim();
        if (cleanIp.endsWith("/")) cleanIp = cleanIp.substring(0, cleanIp.length() - 1);
        
        // Use port 80 explicitly for HTTP if not provided
        String finalUrl = "http://" + cleanIp + "/";

        return new Retrofit.Builder()
                .baseUrl(finalUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .writeTimeout(5, TimeUnit.SECONDS)
                        .build())
                .build()
                .create(ApiService.class);
    }
}
