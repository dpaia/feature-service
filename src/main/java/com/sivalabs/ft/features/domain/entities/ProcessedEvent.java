package com.sivalabs.ft.features.domain.entities;

import com.sivalabs.ft.features.domain.models.EventType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity for storing processed event IDs for deduplication
 * Uses composite primary key (event_id, event_type) for dual-level deduplication
 * Supports both API-level idempotency and Event-level deduplication
 */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "result_data")
    private String resultData;

    // Default constructor for JPA
    protected ProcessedEvent() {}

    public ProcessedEvent(String eventId, EventType eventType, LocalDateTime processedAt, LocalDateTime expiresAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
        this.expiresAt = expiresAt;
    }

    public ProcessedEvent(
            String eventId,
            EventType eventType,
            LocalDateTime processedAt,
            LocalDateTime expiresAt,
            String resultData) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
        this.expiresAt = expiresAt;
        this.resultData = resultData;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getResultData() {
        return resultData;
    }

    public void setResultData(String resultData) {
        this.resultData = resultData;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
