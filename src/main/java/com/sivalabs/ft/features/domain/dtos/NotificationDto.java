package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.DeliveryStatus;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        String recipientUserId,
        NotificationEventType eventType,
        String eventDetails,
        String link,
        Instant createdAt,
        boolean read,
        Instant readAt,
        DeliveryStatus deliveryStatus)
        implements Serializable {

    /**
     * Create a copy with read status updated
     */
    public NotificationDto markAsRead(Instant readAt) {
        return new NotificationDto(
                id, recipientUserId, eventType, eventDetails, link, createdAt, true, readAt, deliveryStatus);
    }

    /**
     * Create a copy with unread status
     */
    public NotificationDto markAsUnread() {
        return new NotificationDto(
                id, recipientUserId, eventType, eventDetails, link, createdAt, false, null, deliveryStatus);
    }

    /**
     * Create a copy with updated delivery status
     */
    public NotificationDto withDeliveryStatus(DeliveryStatus status) {
        return new NotificationDto(id, recipientUserId, eventType, eventDetails, link, createdAt, read, readAt, status);
    }
}
