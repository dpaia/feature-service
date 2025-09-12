package com.sivalabs.ft.features.domain.entities;

import com.sivalabs.ft.features.domain.models.EventType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity for storing all feature events with full metadata for replay support
 * Uses composite primary key (event_id, event_type) for dual-level deduplication
 * Supports both API-level idempotency and Event-level deduplication with replay capability
 */
@Entity
@Table(name = "event_store")
@IdClass(EventStoreId.class)
public class EventStore {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "operation_type", nullable = false)
    private String operationType;

    @Column(name = "feature_id")
    private Long featureId;

    @Column(name = "feature_code")
    private String featureCode;

    @Column(name = "event_payload", nullable = false, columnDefinition = "TEXT")
    private String eventPayload;

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "result_data", columnDefinition = "TEXT")
    private String resultData;

    @Column(name = "replay_count", nullable = false)
    private Integer replayCount = 0;

    @Column(name = "last_replayed_at")
    private LocalDateTime lastReplayedAt;

    @Column(name = "replay_status")
    private String replayStatus; // NULL = never replayed, 'SUCCESS' = success, 'FAILED' = failed

    // Default constructor for JPA
    protected EventStore() {}

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

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public Long getFeatureId() {
        return featureId;
    }

    public void setFeatureId(Long featureId) {
        this.featureId = featureId;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    public String getEventPayload() {
        return eventPayload;
    }

    public void setEventPayload(String eventPayload) {
        this.eventPayload = eventPayload;
    }

    public LocalDateTime getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(LocalDateTime eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
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

    public Integer getReplayCount() {
        return replayCount;
    }

    public void setReplayCount(Integer replayCount) {
        this.replayCount = replayCount;
    }

    public LocalDateTime getLastReplayedAt() {
        return lastReplayedAt;
    }

    public void setLastReplayedAt(LocalDateTime lastReplayedAt) {
        this.lastReplayedAt = lastReplayedAt;
    }

    public String getReplayStatus() {
        return replayStatus;
    }

    public void setReplayStatus(String replayStatus) {
        this.replayStatus = replayStatus;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
