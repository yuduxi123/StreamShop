package com.bytedance.streamshop.ui.feed;

import androidx.media3.common.Player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoReplayPolicyTest {
    @Test
    public void endedPlaybackRestartsFromBeginning() {
        assertTrue(VideoReplayPolicy.shouldRestartFromBeginning(
                Player.STATE_ENDED,
                10_000,
                10_000));
    }

    @Test
    public void nearEndPlaybackRestartsFromBeginning() {
        assertTrue(VideoReplayPolicy.shouldRestartFromBeginning(
                Player.STATE_READY,
                9_700,
                10_000));
    }

    @Test
    public void midPlaybackDoesNotRestartFromBeginning() {
        assertFalse(VideoReplayPolicy.shouldRestartFromBeginning(
                Player.STATE_READY,
                4_000,
                10_000));
    }
}
