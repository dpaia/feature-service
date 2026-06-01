package com.sivalabs.ft.features.domain.events.internal;

import com.sivalabs.ft.features.domain.entities.Feature;
import java.time.Instant;

/**
 * Internal event that is published when a feature is deleted.
 * This event is published within the transaction and will be processed
 * by a TransactionalEventListener after the transaction commits.
 */
public record InternalFeatureDeletedEvent(
        Feature feature,
        String deletedBy,
        Instant deletedAt) {
    // Additional information about who deleted the feature and when
}