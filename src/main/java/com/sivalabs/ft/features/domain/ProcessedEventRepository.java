package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.ProcessedEvent;
import com.sivalabs.ft.features.domain.entities.ProcessedEventId;
import com.sivalabs.ft.features.domain.models.EventType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for ProcessedEvent entities with PostgreSQL UPSERT optimization
 * Uses composite primary key (event_id, event_type) for dual-level deduplication
 * Uses ON CONFLICT DO NOTHING for atomic deduplication operations
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    /**
     * Insert event ID with type and result data if not exists using PostgreSQL UPSERT
     * This is atomic and thread-safe - returns 1 if inserted, 0 if already exists
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
            INSERT INTO processed_events (event_id, event_type, processed_at, expires_at, result_data)
            VALUES (:eventId, :eventType, :processedAt, :expiresAt, :resultData)
            ON CONFLICT (event_id, event_type) DO NOTHING
            """,
            nativeQuery = true)
    int insertIfNotExistsWithResult(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("processedAt") LocalDateTime processedAt,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("resultData") String resultData);

    /**
     * Get result data for existing event
     */
    @Query(
            value =
                    """
            SELECT result_data FROM processed_events
            WHERE event_id = :eventId AND event_type = :eventType AND expires_at > :now
            """,
            nativeQuery = true)
    String getResultData(
            @Param("eventId") String eventId, @Param("eventType") String eventType, @Param("now") LocalDateTime now);

    /**
     * Update the result data for an existing processed event.
     * This is used to replace the "PROCESSING" marker with the actual result.
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
            UPDATE processed_events SET result_data = :resultData
            WHERE event_id = :eventId AND event_type = :eventType
            """,
            nativeQuery = true)
    int updateResultData(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("resultData") String resultData);

    /**
     * Check if event exists with specific type (simple version for tests)
     */
    @Query("SELECT COUNT(p) > 0 FROM ProcessedEvent p WHERE p.eventId = :eventId AND p.eventType = :eventType")
    boolean existsByEventIdAndEventType(@Param("eventId") String eventId, @Param("eventType") EventType eventType);

    /**
     * Clean up expired events (TTL mechanism)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ProcessedEvent p WHERE p.expiresAt <= :now")
    int deleteExpiredEvents(@Param("now") LocalDateTime now);

    /**
     * Count events by type (for monitoring)
     */
    @Query("SELECT COUNT(p) FROM ProcessedEvent p WHERE p.eventType = :eventType AND p.expiresAt > :now")
    long countByEventTypeAndNotExpired(@Param("eventType") EventType eventType, @Param("now") LocalDateTime now);
}
