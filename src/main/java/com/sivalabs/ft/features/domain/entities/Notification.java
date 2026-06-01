package com.sivalabs.ft.features.domain.entities;

import com.sivalabs.ft.features.domain.models.DeliveryStatus;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Size(max = 255) @NotNull @Column(name = "recipient_user_id", nullable = false)
    private String recipientUserId;

    @NotNull @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private NotificationEventType eventType;

    @Column(name = "event_details", length = Integer.MAX_VALUE)
    private String eventDetails;

    @Size(max = 500) @Column(name = "link", length = 500)
    private String link;

    @NotNull @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull @ColumnDefault("false")
    @Column(name = "read", nullable = false)
    private Boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @NotNull @Column(name = "delivery_status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PENDING'")
    private DeliveryStatus deliveryStatus;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(String recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public NotificationEventType getEventType() {
        return eventType;
    }

    public void setEventType(NotificationEventType eventType) {
        this.eventType = eventType;
    }

    public String getEventDetails() {
        return eventDetails;
    }

    public void setEventDetails(String eventDetails) {
        this.eventDetails = eventDetails;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    /**
     * Mark notification as read and set readAt timestamp
     */
    public void markAsRead() {
        this.read = true;
        this.readAt = Instant.now();
    }

    /**
     * Mark notification as unread and clear readAt timestamp
     */
    public void markAsUnread() {
        this.read = false;
        this.readAt = null;
    }

    /**
     * Update delivery status
     */
    public void updateDeliveryStatus(DeliveryStatus status) {
        this.deliveryStatus = status;
    }
}
