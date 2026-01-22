package com.sivalabs.ft.features.domain.dtos;

import com.sivalabs.ft.features.domain.models.NotificationEventType;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record EmailDeliveryFailureDto(
        UUID id,
        UUID notificationId,
        String recipientEmail,
        NotificationEventType eventType,
        String errorMessage,
        Instant failedAt)
        implements Serializable {}
