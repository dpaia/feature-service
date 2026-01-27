package com.sivalabs.ft.features.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_delivery_failures")
public class EmailDeliveryFailure {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Size(max = 255) @NotNull @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Size(max = 50) @NotNull @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @NotNull @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @NotNull @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(UUID notificationId) {
        this.notificationId = notificationId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }
}
