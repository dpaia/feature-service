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

    @Size(max = 255) @NotNull @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @NotNull @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private NotificationEventType eventType;

    @NotNull @Column(name = "error_message", nullable = false, length = Integer.MAX_VALUE)
    private String errorMessage;

    @NotNull @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", insertable = false, updatable = false)
    private Notification notification;

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

    public Notification getNotification() {
        return notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }
}
