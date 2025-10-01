package com.sivalabs.ft.features.domain.models;

/**
 * Delivery status for notifications
 * Tracks the delivery state of email notifications
 */
public enum DeliveryStatus {
    /**
     * Notification is pending delivery
     */
    PENDING,

    /**
     * Notification was successfully delivered
     */
    DELIVERED,

    /**
     * Notification delivery failed
     */
    FAILED
}
