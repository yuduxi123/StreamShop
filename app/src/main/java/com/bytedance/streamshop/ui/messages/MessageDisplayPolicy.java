package com.bytedance.streamshop.ui.messages;

final class MessageDisplayPolicy {
    private MessageDisplayPolicy() {}

    static boolean isSentByCurrentUser(String currentUserId, Object senderId) {
        return currentUserId != null && senderId != null
                && currentUserId.equals(String.valueOf(senderId));
    }
}
