package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Notification;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

interface NotificationRepository extends ListCrudRepository<Notification, UUID> {

    /**
     * Find all notifications for a specific user with pagination, ordered by creation date (newest first)
     */
    @Query("SELECT n FROM Notification n WHERE n.recipientUserId = :recipientUserId ORDER BY n.createdAt DESC")
    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId, Pageable pageable);

    /**
     * Mark notification as read
     */
    @Modifying
    @Query(
            "UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.id = :id AND n.recipientUserId = :recipientUserId")
    int markAsRead(UUID id, String recipientUserId, Instant readAt);

    /**
     * Mark notification as unread
     */
    @Modifying
    @Query(
            "UPDATE Notification n SET n.read = false, n.readAt = null WHERE n.id = :id AND n.recipientUserId = :recipientUserId")
    int markAsUnread(UUID id, String recipientUserId);
}
