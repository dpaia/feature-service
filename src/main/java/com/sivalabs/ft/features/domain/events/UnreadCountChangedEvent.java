package com.sivalabs.ft.features.domain.events;

public record UnreadCountChangedEvent(String type, String recipientUserId, long unreadCount) {
    public static UnreadCountChangedEvent of(String recipientUserId, long unreadCount) {
        return new UnreadCountChangedEvent("UnreadCountChanged", recipientUserId, unreadCount);
    }
}
