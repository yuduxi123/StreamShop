package com.bytedance.streamshop.ui.feed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FeedViewModelStateTest {
    @Test
    public void remembersFeedPositionAndHomeTabAcrossFragmentRecreation() {
        HomeStateViewModel state = new HomeStateViewModel();

        state.setFeedPosition(3);
        state.setHomeTabPosition(1);

        assertEquals(3, state.getFeedPosition());
        assertEquals(1, state.getHomeTabPosition());
    }

    @Test
    public void clampsNegativePositionsToZero() {
        HomeStateViewModel state = new HomeStateViewModel();

        state.setFeedPosition(-4);
        state.setHomeTabPosition(-1);

        assertEquals(0, state.getFeedPosition());
        assertEquals(0, state.getHomeTabPosition());
    }
}
