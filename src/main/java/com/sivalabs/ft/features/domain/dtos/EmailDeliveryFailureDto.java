package com.sivalabs.ft.features.domain.dtos;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record EmailDeliveryFailureDto(
        UUID id, UUID notificationId, String recipientEmail, String eventType, String errorMessage, Instant failedAt)
        implements Serializable {}
