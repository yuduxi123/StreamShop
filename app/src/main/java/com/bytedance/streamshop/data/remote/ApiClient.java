package com.bytedance.streamshop.data.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import android.util.Log;

public class ApiClient {
    // 雷电模拟器不支持 10.0.2.2，用电脑局域网 IP
    // 如果换了 WiFi 导致 IP 变化，需要同步修改这里
    private static final String BASE_URL = "http://10.208.69.9:3000/api/";
    private static ApiClient instance;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private String authToken;
    private String currentUsername;
    private String currentAccount;
    private String currentUserId;
    private String currentAvatarUrl;

    private ApiClient() {
        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .create();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor())
                .addInterceptor(new RetryInterceptor())
                .build();
    }

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public Gson getGson() {
        return gson;
    }

    public String getBaseUrl() {
        return BASE_URL;
    }

    public void setAuthToken(String token) {
        this.authToken = token;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    public void clearAuthToken() {
        this.authToken = null;
        this.currentUsername = null;
        this.currentAccount = null;
        this.currentUserId = null;
        this.currentAvatarUrl = null;
    }

    public boolean isAuthenticated() {
        return authToken != null && !authToken.isEmpty();
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    public String getCurrentAccount() {
        return currentAccount;
    }

    public void setCurrentAccount(String account) {
        this.currentAccount = account;
    }

    public String getCurrentAvatarUrl() {
        return currentAvatarUrl;
    }

    public void setCurrentAvatarUrl(String avatarUrl) {
        this.currentAvatarUrl = avatarUrl;
    }

    private class AuthInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request original = chain.request();
            if (authToken != null) {
                Request.Builder builder = original.newBuilder()
                        .header("Authorization", "Bearer " + authToken);
                if (original.method().equals("GET") || original.method().equals("POST")
                        || original.method().equals("PATCH") || original.method().equals("DELETE")) {
                    builder.header("Content-Type", "application/json");
                }
                return chain.proceed(builder.build());
            }
            return chain.proceed(original);
        }
    }

    private static class RetryInterceptor implements Interceptor {
        private static final int MAX_RETRIES = 2;

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = null;
            IOException lastEx = null;

            for (int i = 0; i <= MAX_RETRIES; i++) {
                try {
                    if (i > 0) {
                        Thread.sleep(1000L * i); // 1s, 2s backoff
                    }
                    response = chain.proceed(request);
                    if (response.isSuccessful()) return response;
                    // Only retry on server errors (5xx)
                    if (response.code() < 500) return response;
                    response.close();
                } catch (IOException e) {
                    lastEx = e;
                    if (i < MAX_RETRIES) {
                        Log.d("ApiClient", "Retry " + (i + 1) + "/" + MAX_RETRIES + " after: " + e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", e);
                }
            }
            throw lastEx != null ? lastEx : new IOException("Request failed after retries");
        }
    }
}
