package com.bytedance.streamshop.ui.feed;

public final class VideoPlaybackErrorPolicy {
    private VideoPlaybackErrorPolicy() {}

    public static boolean shouldUseDemoFallbackOnSourceError() {
        return false;
    }
}
