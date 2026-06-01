package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;

/**
 * Event that is published when a new feature is created.
 * Extends the base FeatureEvent class.
 */
public class FeatureCreatedEvent extends FeatureEvent {

    public FeatureCreatedEvent(
            Long id,
            String code,
            String title,
            String description,
            FeatureStatus status,
            String releaseCode,
            String assignedTo,
            String createdBy,
            Instant createdAt) {
        super(id, code, title, description, status, releaseCode, assignedTo, createdBy, createdAt);
    }
}
