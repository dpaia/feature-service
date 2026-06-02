package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.EventStore;
import com.sivalabs.ft.features.domain.entities.EventStoreId;
import com.sivalabs.ft.features.domain.models.EventType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for EventStore entities with PostgreSQL UPSERT optimization
 * Uses composite primary key (event_id, event_type) for dual-level deduplication
 * Supports event replay functionality with time range and feature filtering
 */
@Repository
public interface EventStoreRepository extends JpaRepository<EventStore, EventStoreId> {

    /**
     * Insert event with full metadata if not exists using PostgreSQL UPSERT
     * This is atomic and thread-safe - returns 1 if inserted, 0 if already exists
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
            INSERT INTO event_store (event_id, event_type, operation_type, feature_id, feature_code,
                                     event_payload, event_timestamp, processed_at, expires_at, result_data,
                                     replay_count, last_replayed_at)
            VALUES (:eventId, :eventType, :operationType, :featureId, :featureCode,
                    :eventPayload, :eventTimestamp, :processedAt, :expiresAt, :resultData,
                    0, NULL)
            ON CONFLICT (event_id, event_type) DO NOTHING
            """,
            nativeQuery = true)
    int insertIfNotExists(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("operationType") String operationType,
            @Param("featureId") Long featureId,
            @Param("featureCode") String featureCode,
            @Param("eventPayload") String eventPayload,
            @Param("eventTimestamp") LocalDateTime eventTimestamp,
            @Param("processedAt") LocalDateTime processedAt,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("resultData") String resultData);

    /**
     * Get result data for existing event
     */
    @Query(
            value =
                    """
            SELECT result_data FROM event_store
            WHERE event_id = :eventId AND event_type = :eventType AND expires_at > :now
            """,
            nativeQuery = true)
    String getResultData(
            @Param("eventId") String eventId, @Param("eventType") String eventType, @Param("now") LocalDateTime now);

    /**
     * Update the result data for an existing event
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
            UPDATE event_store SET result_data = :resultData
            WHERE event_id = :eventId AND event_type = :eventType
            """,
            nativeQuery = true)
    int updateResultData(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("resultData") String resultData);

    /**
     * Check if event exists with specific type
     */
    @Query("SELECT COUNT(e) > 0 FROM EventStore e WHERE e.eventId = :eventId AND e.eventType = :eventType")
    boolean existsByEventIdAndEventType(@Param("eventId") String eventId, @Param("eventType") EventType eventType);

    /**
     * Clean up expired events (TTL mechanism)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM EventStore e WHERE e.expiresAt <= :now")
    int deleteExpiredEvents(@Param("now") LocalDateTime now);

    /**
     * Count events by type (for monitoring)
     */
    @Query("SELECT COUNT(e) FROM EventStore e WHERE e.eventType = :eventType AND e.expiresAt > :now")
    long countByEventTypeAndNotExpired(@Param("eventType") EventType eventType, @Param("now") LocalDateTime now);

    // ========== Event Replay Methods ==========

    /**
     * Find all events within a time range for replay
     * Ordered by event_timestamp to maintain event order
     */
    @Query(
            """
        SELECT e FROM EventStore e
        WHERE e.eventTimestamp >= :startTime
        AND e.eventTimestamp <= :endTime
        AND e.eventType = :eventType
        ORDER BY e.eventTimestamp ASC
        """)
    List<EventStore> findEventsForReplay(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("eventType") EventType eventType);

    /**
     * Find events for specific feature codes within time range
     */
    @Query(
            """
        SELECT e FROM EventStore e
        WHERE e.eventTimestamp >= :startTime
        AND e.eventTimestamp <= :endTime
        AND e.featureCode IN :featureCodes
        AND e.eventType = :eventType
        ORDER BY e.eventTimestamp ASC
        """)
    List<EventStore> findEventsForReplayByFeatures(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("featureCodes") List<String> featureCodes,
            @Param("eventType") EventType eventType);

    /**
     * Find events by operation type within time range
     */
    @Query(
            """
        SELECT e FROM EventStore e
        WHERE e.eventTimestamp >= :startTime
        AND e.eventTimestamp <= :endTime
        AND e.operationType = :operationType
        AND e.eventType = :eventType
        ORDER BY e.eventTimestamp ASC
        """)
    List<EventStore> findEventsForReplayByOperation(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("operationType") String operationType,
            @Param("eventType") EventType eventType);

    /**
     * Find all events for a specific feature (all operations)
     */
    @Query(
            """
        SELECT e FROM EventStore e
        WHERE e.featureCode = :featureCode
        AND e.eventType = :eventType
        ORDER BY e.eventTimestamp ASC
        """)
    List<EventStore> findEventsByFeatureCode(
            @Param("featureCode") String featureCode, @Param("eventType") EventType eventType);

    /**
     * Count events in time range for monitoring
     */
    @Query(
            """
        SELECT COUNT(e) FROM EventStore e
        WHERE e.eventTimestamp >= :startTime
        AND e.eventTimestamp <= :endTime
        AND e.eventType = :eventType
        """)
    long countEventsInTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("eventType") EventType eventType);

    // ========== Replay Tracking Methods ==========

    /**
     * Update replay tracking when event is successfully replayed
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
            UPDATE event_store
            SET replay_count = replay_count + 1,
                last_replayed_at = :replayedAt,
                replay_status = 'SUCCESS'
            WHERE event_id = :eventId AND event_type = :eventType
            """,
            nativeQuery = true)
    int updateReplayTrackingSuccess(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("replayedAt") LocalDateTime replayedAt);

    /**
     * Update replay tracking when event replay fails
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
            UPDATE event_store
            SET last_replayed_at = :replayedAt,
                replay_status = 'FAILED'
            WHERE event_id = :eventId AND event_type = :eventType
            """,
            nativeQuery = true)
    int updateReplayTrackingFailure(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("replayedAt") LocalDateTime replayedAt);

    /**
     * Update replay tracking when event is replayed (backward compatibility)
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
            UPDATE event_store
            SET replay_count = replay_count + 1,
                last_replayed_at = :replayedAt,
                replay_status = 'SUCCESS'
            WHERE event_id = :eventId AND event_type = :eventType
            """,
            nativeQuery = true)
    int updateReplayTracking(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("replayedAt") LocalDateTime replayedAt);

    /**
     * Find events that have been replayed (for monitoring)
     */
    @Query("SELECT e FROM EventStore e WHERE e.replayCount > 0 ORDER BY e.lastReplayedAt DESC")
    List<EventStore> findReplayedEvents();

    /**
     * Count events by replay status
     */
    @Query("SELECT COUNT(e) FROM EventStore e WHERE e.replayCount > 0 AND e.eventType = :eventType")
    long countReplayedEvents(@Param("eventType") EventType eventType);

    /**
     * Find events that failed during replay within time range
     */
    @Query(
            """
        SELECT e FROM EventStore e
        WHERE e.eventTimestamp >= :startTime
        AND e.eventTimestamp <= :endTime
        AND e.replayStatus = 'FAILED'
        AND e.eventType = :eventType
        ORDER BY e.lastReplayedAt DESC
        """)
    List<EventStore> findFailedReplayEvents(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("eventType") EventType eventType);

    /**
     * Count events that failed during replay within time range
     */
    @Query(
            """
        SELECT COUNT(e) FROM EventStore e
        WHERE e.eventTimestamp >= :startTime
        AND e.eventTimestamp <= :endTime
        AND e.replayStatus = 'FAILED'
        AND e.eventType = :eventType
        """)
    long countFailedReplayEvents(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("eventType") EventType eventType);
}
