package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.EmailDeliveryFailure;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface EmailDeliveryFailureRepository extends ListCrudRepository<EmailDeliveryFailure, UUID> {

    /**
     * Find all email delivery failures with pagination, ordered by failed_at DESC (newest first)
     */
    @Query("SELECT edf FROM EmailDeliveryFailure edf ORDER BY edf.failedAt DESC")
    Page<EmailDeliveryFailure> findAllOrderByFailedAtDesc(Pageable pageable);

    /**
     * Find email delivery failures by date with pagination, ordered by failed_at DESC (newest first)
     * Date filtering is done by comparing the date part of failed_at with the provided date
     */
    @Query(
            "SELECT edf FROM EmailDeliveryFailure edf WHERE edf.failedAt >= :startOfDay AND edf.failedAt < :endOfDay ORDER BY edf.failedAt DESC")
    Page<EmailDeliveryFailure> findByDateOrderByFailedAtDesc(
            @Param("startOfDay") Instant startOfDay, @Param("endOfDay") Instant endOfDay, Pageable pageable);

    /**
     * Find all email delivery failures for a specific notification, ordered by failed_at DESC (newest first)
     */
    @Query(
            "SELECT edf FROM EmailDeliveryFailure edf WHERE edf.notificationId = :notificationId ORDER BY edf.failedAt DESC")
    List<EmailDeliveryFailure> findByNotificationIdOrderByFailedAtDesc(@Param("notificationId") UUID notificationId);
}
