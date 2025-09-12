package com.sivalabs.ft.features.domain.models;

/**
 * Event types for deduplication
 * Separates API-level events from Kafka event-level events
 */
public enum EventType {
    /**
     * API-level events - for API idempotency
     * Used when processing REST API requests
     */
    API,

    /**
     * Event-level events - for Kafka event deduplication
     * Used when processing Kafka events in listeners
     */
    EVENT
}
