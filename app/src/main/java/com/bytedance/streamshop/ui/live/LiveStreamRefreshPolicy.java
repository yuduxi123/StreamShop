package com.bytedance.streamshop.ui.live;

public final class LiveStreamRefreshPolicy {
    private LiveStreamRefreshPolicy() {}

    public static boolean shouldPrepareStream(String currentStreamUrl,
                                              boolean streamLoaded,
                                              String nextStreamUrl) {
        if (nextStreamUrl == null || nextStreamUrl.trim().isEmpty()) {
            return false;
        }
        return !streamLoaded || currentStreamUrl == null || !currentStreamUrl.equals(nextStreamUrl);
    }
}
