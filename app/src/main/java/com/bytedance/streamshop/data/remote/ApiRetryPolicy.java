package com.bytedance.streamshop.data.remote;

final class ApiRetryPolicy {
    private ApiRetryPolicy() {
    }

    static boolean shouldRetryResponse(int statusCode, int attempt, int maxRetries) {
        return statusCode >= 500 && attempt < maxRetries;
    }
}
