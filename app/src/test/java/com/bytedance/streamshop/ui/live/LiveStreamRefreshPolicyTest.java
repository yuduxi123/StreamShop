package com.bytedance.streamshop.ui.live;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveStreamRefreshPolicyTest {
    @Test
    public void preparesFirstNonEmptyStream() {
        assertTrue(LiveStreamRefreshPolicy.shouldPrepareStream(null, false,
                "http://10.208.69.9:8000/live/room-1.flv"));
    }

    @Test
    public void doesNotPrepareSameLoadedStreamAgain() {
        String streamUrl = "http://10.208.69.9:8000/live/room-1.flv";

        assertFalse(LiveStreamRefreshPolicy.shouldPrepareStream(streamUrl, true, streamUrl));
    }

    @Test
    public void preparesWhenStreamUrlChanges() {
        assertTrue(LiveStreamRefreshPolicy.shouldPrepareStream(
                "http://10.208.69.9:8000/live/old.flv",
                true,
                "http://10.208.69.9:8000/live/new.flv"));
    }

    @Test
    public void ignoresMissingStreamUrl() {
        assertFalse(LiveStreamRefreshPolicy.shouldPrepareStream(null, false, ""));
        assertFalse(LiveStreamRefreshPolicy.shouldPrepareStream(null, false, null));
    }
}
