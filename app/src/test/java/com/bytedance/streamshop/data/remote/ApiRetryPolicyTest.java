package com.bytedance.streamshop.data.remote;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApiRetryPolicyTest {
    @Test
    public void retriesServerErrorsBeforeFinalAttempt() {
        assertTrue(ApiRetryPolicy.shouldRetryResponse(503, 0, 2));
        assertTrue(ApiRetryPolicy.shouldRetryResponse(500, 1, 2));
    }

    @Test
    public void returnsServerErrorResponseOnFinalAttempt() {
        assertFalse(ApiRetryPolicy.shouldRetryResponse(503, 2, 2));
    }

    @Test
    public void doesNotRetryClientErrorsOrSuccessfulResponses() {
        assertFalse(ApiRetryPolicy.shouldRetryResponse(400, 0, 2));
        assertFalse(ApiRetryPolicy.shouldRetryResponse(200, 0, 2));
    }
}
