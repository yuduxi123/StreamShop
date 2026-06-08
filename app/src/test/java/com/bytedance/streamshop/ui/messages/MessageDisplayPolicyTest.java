package com.bytedance.streamshop.ui.messages;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageDisplayPolicyTest {
    @Test
    public void currentUserMessageIsSent() {
        assertTrue(MessageDisplayPolicy.isSentByCurrentUser("user-1", "user-1"));
    }

    @Test
    public void otherUserMessageIsReceived() {
        assertFalse(MessageDisplayPolicy.isSentByCurrentUser("user-1", "user-2"));
    }

    @Test
    public void missingCurrentUserIsReceived() {
        assertFalse(MessageDisplayPolicy.isSentByCurrentUser(null, "user-1"));
    }
}
