package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;

public class FeatureDeletedEvent extends FeatureEvent {
    private final String updatedBy;
    private final Instant updatedAt;
    private final String deletedBy;
    private final Instant deletedAt;

    public FeatureDeletedEvent(
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
            Instant updatedAt,
            String deletedBy,
            Instant deletedAt) {
        super(id, code, title, description, status, releaseCode, assignedTo, createdBy, createdAt);
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
        this.deletedBy = deletedBy;
        this.deletedAt = deletedAt;
    }

    /**
     * @return the user who last updated the feature
     */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * @return the timestamp when the feature was last updated
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * @return the user who deleted the feature
     */
    public String getDeletedBy() {
        return deletedBy;
    }

    /**
     * @return the timestamp when the feature was deleted
     */
    public Instant getDeletedAt() {
        return deletedAt;
    }
}
