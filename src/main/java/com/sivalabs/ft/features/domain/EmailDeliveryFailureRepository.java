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

    @Query("SELECT f FROM EmailDeliveryFailure f ORDER BY f.failedAt DESC")
    Page<EmailDeliveryFailure> findAllOrderByFailedAtDesc(Pageable pageable);

    @Query(
            "SELECT f FROM EmailDeliveryFailure f WHERE f.failedAt >= :start AND f.failedAt < :end ORDER BY f.failedAt DESC")
    Page<EmailDeliveryFailure> findByFailedAtBetweenOrderByFailedAtDesc(Instant start, Instant end, Pageable pageable);

    @Query("SELECT f FROM EmailDeliveryFailure f WHERE f.notificationId = :notificationId ORDER BY f.failedAt DESC")
    List<EmailDeliveryFailure> findByNotificationIdOrderByFailedAtDesc(UUID notificationId);
}
