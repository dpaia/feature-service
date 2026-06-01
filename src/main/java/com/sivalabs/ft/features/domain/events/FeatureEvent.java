package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;

/**
 * Base class for all feature-related events.
 * This class defines the common properties shared by all feature events.
 * Subclasses should extend this class to add event-specific properties.
 */
public abstract class FeatureEvent {
    private final Long id;
    private final String code;
    private final String title;
    private final String description;
    private final FeatureStatus status;
    private final String releaseCode;
    private final String assignedTo;
    private final String createdBy;
    private final Instant createdAt;

    protected FeatureEvent(
            Long id,
            String code,
            String title,
            String description,
            FeatureStatus status,
            String releaseCode,
            String assignedTo,
            String createdBy,
            Instant createdAt) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.description = description;
        this.status = status;
        this.releaseCode = releaseCode;
        this.assignedTo = assignedTo;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public FeatureStatus getStatus() {
        return status;
    }

    public String getReleaseCode() {
        return releaseCode;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}