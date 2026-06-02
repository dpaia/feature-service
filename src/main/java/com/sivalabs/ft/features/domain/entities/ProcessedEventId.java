package com.sivalabs.ft.features.domain.entities;

import com.sivalabs.ft.features.domain.models.EventType;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for ProcessedEvent entity
 * Supports dual-level deduplication with (event_id, event_type)
 */
public class ProcessedEventId implements Serializable {

    private String eventId;
    private EventType eventType;

    // Default constructor for JPA
    public ProcessedEventId() {}

    public ProcessedEventId(String eventId, EventType eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedEventId that = (ProcessedEventId) o;
        return Objects.equals(eventId, that.eventId) && eventType == that.eventType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, eventType);
    }

    @Override
    public String toString() {
        return "ProcessedEventId{" + "eventId='" + eventId + '\'' + ", eventType=" + eventType + '}';
    }
}
