package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.models.ActionType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record FeatureUsageEvent(
        String eventId,
        String userId,
        String featureCode,
        String productCode,
        String releaseCode,
        ActionType actionType,
        Instant timestamp,
        Map<String, Object> context,
        String ipAddress,
        String userAgent) {
    public FeatureUsageEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
    }
}
