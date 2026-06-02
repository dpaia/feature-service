package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.NotificationDto;
import com.sivalabs.ft.features.domain.entities.Notification;
import com.sivalabs.ft.features.domain.events.UnreadCountChangedPublisher;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.NotificationMapper;
import com.sivalabs.ft.features.domain.models.DeliveryStatus;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationEmailService notificationEmailService;
    private final UnreadCountChangedPublisher unreadCountChangedPublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationMapper notificationMapper,
            NotificationEmailService notificationEmailService,
            UnreadCountChangedPublisher unreadCountChangedPublisher) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.notificationEmailService = notificationEmailService;
        this.unreadCountChangedPublisher = unreadCountChangedPublisher;
    }

    /**
     * Create a new notification
     */
    @Transactional
    public NotificationDto createNotification(
            String recipientUserId,
            String recipientEmail,
            NotificationEventType eventType,
            String eventDetails,
            String link) {

        long previousUnreadCount = notificationRepository.countUnread(recipientUserId);

        var notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setRecipientEmail(recipientEmail);
        notification.setEventType(eventType);
        notification.setEventDetails(eventDetails);
        notification.setLink(link);
        notification.setCreatedAt(Instant.now());
        notification.setRead(false);
        notification.setDeliveryStatus(DeliveryStatus.PENDING);

        notification = notificationRepository.save(notification);

        log.info("Created notification {} for user {}", notification.getId(), recipientUserId);

        // Send email after transaction commits to avoid sending if rollback occurs
        Notification savedNotification = notification;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationEmailService.sendNotificationEmail(savedNotification);
                publishUnreadCountIfChanged(recipientUserId, previousUnreadCount);
            }
        });

        return notificationMapper.toDto(notification);
    }

    /**
     * Create multiple notifications in a single batch operation.
     */
    @Transactional
    public List<NotificationDto> createNotificationsBatch(List<NotificationData> notificationsData) {
        if (notificationsData == null || notificationsData.isEmpty()) {
            return List.of();
        }

        List<Notification> notifications = new ArrayList<>();
        Instant now = Instant.now();

        for (NotificationData data : notificationsData) {
            var notification = new Notification();
            notification.setRecipientUserId(data.recipientUserId());
            notification.setRecipientEmail(data.recipientEmail());
            notification.setEventType(data.eventType());
            notification.setEventDetails(data.eventDetails());
            notification.setLink(data.link());
            notification.setCreatedAt(now);
            notification.setRead(false);
            notification.setDeliveryStatus(DeliveryStatus.PENDING);
            notifications.add(notification);
        }

        List<Notification> savedNotifications = notificationRepository.saveAll(notifications);

        log.info("Created {} notifications in batch", savedNotifications.size());

        // Send emails after transaction commits to avoid sending if rollback occurs
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishUnreadCountForBatch(savedNotifications);
                for (Notification savedNotification : savedNotifications) {
                    notificationEmailService.sendNotificationEmail(savedNotification);
                }
            }
        });

        return savedNotifications.stream().map(notificationMapper::toDto).toList();
    }

    /**
     * Data class for batch notification creation
     */
    public record NotificationData(
            String recipientUserId,
            String recipientEmail,
            NotificationEventType eventType,
            String eventDetails,
            String link) {}

    /**
     * Get notifications for a user with pagination
     */
    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotificationsForUser(String recipientUserId, Pageable pageable) {
        return getNotificationsForUser(recipientUserId, null, pageable);
    }

    /**
     * Get notifications for a user with pagination and optional read status
     */
    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotificationsForUser(String recipientUserId, Boolean read, Pageable pageable) {
        Page<Notification> notifications = read == null
                ? notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, pageable)
                : notificationRepository.findByRecipientUserIdAndReadOrderByCreatedAtDesc(
                        recipientUserId, read, pageable);
        return notifications.map(notificationMapper::toDto);
    }

    /**
     * Mark notification as read
     * Only the recipient can mark their own notifications as read
     */
    @Transactional
    public NotificationDto markAsRead(UUID notificationId, String recipientUserId) {
        long previousUnreadCount = notificationRepository.countUnread(recipientUserId);
        Instant readAt = Instant.now();
        int updated = notificationRepository.markAsRead(notificationId, recipientUserId, readAt);

        if (updated == 0) {
            throw new ResourceNotFoundException("Notification not found or access denied");
        }

        // Fetch updated notification
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        log.info("Marked notification {} as read for user {}", notificationId, recipientUserId);

        publishUnreadCountAfterCommit(recipientUserId, previousUnreadCount);

        return notificationMapper.toDto(notification);
    }

    /**
     * Mark notification as unread
     * Only the recipient can mark their own notifications as unread
     */
    @Transactional
    public NotificationDto markAsUnread(UUID notificationId, String recipientUserId) {
        long previousUnreadCount = notificationRepository.countUnread(recipientUserId);
        int updated = notificationRepository.markAsUnread(notificationId, recipientUserId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Notification not found or access denied");
        }

        // Fetch updated notification
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        log.info("Marked notification {} as unread for user {}", notificationId, recipientUserId);

        publishUnreadCountAfterCommit(recipientUserId, previousUnreadCount);

        return notificationMapper.toDto(notification);
    }

    /**
     * Mark all notifications as read for a user
     */
    @Transactional
    public long markAllAsRead(String recipientUserId) {
        long previousUnreadCount = notificationRepository.countUnread(recipientUserId);
        Instant readAt = Instant.now();
        long updated = notificationRepository.markAllAsRead(recipientUserId, readAt);
        publishUnreadCountAfterCommit(recipientUserId, previousUnreadCount);
        log.info("Marked {} notifications as read for user {}", updated, recipientUserId);
        return updated;
    }

    void publishUnreadCountAfterCommit(String recipientUserId, long previousUnreadCount) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishUnreadCountIfChanged(recipientUserId, previousUnreadCount);
            }
        });
    }

    void publishUnreadCountIfChanged(String recipientUserId, long previousUnreadCount) {
        long currentUnreadCount = notificationRepository.countUnread(recipientUserId);
        if (currentUnreadCount != previousUnreadCount) {
            unreadCountChangedPublisher.publish(recipientUserId, currentUnreadCount);
        }
    }

    void publishUnreadCountForBatch(List<Notification> savedNotifications) {
        savedNotifications.stream()
                .map(Notification::getRecipientUserId)
                .distinct()
                .forEach(recipientUserId -> {
                    long currentUnreadCount = notificationRepository.countUnread(recipientUserId);
                    unreadCountChangedPublisher.publish(recipientUserId, currentUnreadCount);
                });
    }
}
