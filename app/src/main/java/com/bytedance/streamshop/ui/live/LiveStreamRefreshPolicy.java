package com.bytedance.streamshop.ui.live;

public final class LiveStreamRefreshPolicy {
    private static final String STATUS_ENDED = "ended";

    private LiveStreamRefreshPolicy() {}

    public static boolean shouldPrepareStream(String currentStreamUrl,
                                              boolean streamLoaded,
                                              String nextStreamUrl) {
        if (nextStreamUrl == null || nextStreamUrl.trim().isEmpty()) {
            return false;
        }
        return !streamLoaded || currentStreamUrl == null || !currentStreamUrl.equals(nextStreamUrl);
    }

    public static boolean isLiveEnded(String status) {
        return STATUS_ENDED.equals(status);
    }

    public static boolean shouldWaitForStream(String status,
                                              boolean streamLoaded,
                                              int retryCount,
                                              int maxRetryCount) {
        return !isLiveEnded(status) && !streamLoaded && retryCount < maxRetryCount;
    }
}
