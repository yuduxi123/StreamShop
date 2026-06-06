package com.bytedance.streamshop.ui.feed;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class VideoPlaybackErrorPolicyTest {
    @Test
    public void sourceErrorDoesNotUseSharedDemoFallback() {
        assertFalse(VideoPlaybackErrorPolicy.shouldUseDemoFallbackOnSourceError());
    }
}
