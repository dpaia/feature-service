package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;

public class FeatureUpdatedEvent extends FeatureEvent {
    private final String updatedBy;
    private final Instant updatedAt;

    public FeatureUpdatedEvent(
            Long id,
            String code,
            String title,
            String description,
            FeatureStatus status,
            String releaseCode,
            String assignedTo,
            String createdBy,
            Instant createdAt,
            String updatedBy,
            Instant updatedAt) {
        super(id, code, title, description, status, releaseCode, assignedTo, createdBy, createdAt);
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
