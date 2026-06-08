package com.bytedance.streamshop.ui.feed;

import androidx.media3.common.Player;

final class VideoReplayPolicy {
    private static final long NEAR_END_RESTART_THRESHOLD_MS = 500;

    private VideoReplayPolicy() {
    }

    static boolean shouldRestartFromBeginning(int playbackState, long positionMs, long durationMs) {
        if (playbackState == Player.STATE_ENDED) {
            return true;
        }
        if (durationMs <= 0 || positionMs < 0) {
            return false;
        }
        long remainingMs = durationMs - Math.min(positionMs, durationMs);
        return remainingMs <= NEAR_END_RESTART_THRESHOLD_MS;
    }
}
