package com.sivalabs.ft.features.domain.events.internal;

import com.sivalabs.ft.features.domain.entities.Feature;

/**
 * Internal event that is published when a feature is updated.
 * This event is published within the transaction and will be processed
 * by a TransactionalEventListener after the transaction commits.
 */
public record InternalFeatureUpdatedEvent(Feature feature) {
    // Feature entity is passed directly to allow the listener to access all feature data
}