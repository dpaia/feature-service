package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.EmailDeliveryFailure;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface EmailDeliveryFailureRepository extends ListCrudRepository<EmailDeliveryFailure, UUID> {

    /**
     * Find all failures with pagination, ordered by failed_at DESC (newest first)
     */
    Page<EmailDeliveryFailure> findAllByOrderByFailedAtDesc(Pageable pageable);

    /**
     * Find failures within a half-open date range [start, end) in UTC, ordered by failed_at DESC.
     * Used for date filtering where start = date 00:00 UTC and end = date + 1 day 00:00 UTC.
     * Half-open interval ensures 23:59:59Z is included but 00:00:00Z next day is excluded.
     */
    @Query(
            "SELECT f FROM EmailDeliveryFailure f WHERE f.failedAt >= :start AND f.failedAt < :end ORDER BY f.failedAt DESC")
    Page<EmailDeliveryFailure> findByFailedAtInRangeOrderByFailedAtDesc(Instant start, Instant end, Pageable pageable);

    /**
     * Find all failures for a specific notification, ordered by failed_at DESC
     */
    List<EmailDeliveryFailure> findByNotificationIdOrderByFailedAtDesc(UUID notificationId);
}
