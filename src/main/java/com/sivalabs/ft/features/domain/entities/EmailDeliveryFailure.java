package com.sivalabs.ft.features.domain.entities;

import com.sivalabs.ft.features.domain.models.NotificationEventType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "email_delivery_failures")
public class EmailDeliveryFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Size(max = 255) @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "event_type", length = 50)
    @Enumerated(EnumType.STRING)
    private NotificationEventType eventType;

    @Column(name = "error_message", length = Integer.MAX_VALUE)
    private String errorMessage;

    @NotNull @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "failed_at", nullable = false)
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

    public NotificationEventType getEventType() {
        return eventType;
    }

    public void setEventType(NotificationEventType eventType) {
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
