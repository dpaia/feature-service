package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;

/**
 * Event that is published when a new feature is created.
 * Contains all the details of the created feature, including the role of the creator.
 */
public record FeatureCreatedEvent(
        Long id,
        String code,
        String title,
        String description,
        FeatureStatus status,
        String releaseCode,
        String assignedTo,
        String createdBy,
        String creatorRole,
        Instant createdAt) {}
