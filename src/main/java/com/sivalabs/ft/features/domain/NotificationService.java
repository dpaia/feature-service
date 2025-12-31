package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.NotificationDto;
import com.sivalabs.ft.features.domain.entities.Notification;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.NotificationMapper;
import com.sivalabs.ft.features.domain.models.DeliveryStatus;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import com.sivalabs.ft.features.notifications.EmailService;
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

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationMapper notificationMapper,
            UserRepository userRepository,
            EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    /**
     * Create a new notification
     */
    @Transactional
    public NotificationDto createNotification(
            String recipientUserId, NotificationEventType eventType, String eventDetails, String link) {

        var notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setEventType(eventType);
        notification.setEventDetails(eventDetails);
        notification.setLink(link);
        notification.setCreatedAt(Instant.now());
        notification.setRead(false);
        notification.setDeliveryStatus(DeliveryStatus.PENDING);

        // Get user email for notification
        var maybeEmail = userRepository.findEmailByUsername(recipientUserId);
        if (maybeEmail.isPresent()) {
            notification.setRecipientEmail(maybeEmail.get());
        }

        notification = notificationRepository.save(notification);

        // Send email notification asynchronously (don't block notification creation)
        try {
            if (notification.getRecipientEmail() != null) {
                var status = emailService.sendNotificationEmail(notification);
                notification.updateDeliveryStatus(status);
                notificationRepository.save(notification);
            }
        } catch (Exception e) {
            // Log failure but don't prevent notification creation
            emailService.logDeliveryFailure(
                    notification.getRecipientEmail(),
                    notification.getEventType().toString(),
                    "Failed to send email: " + e.getMessage());
            notification.updateDeliveryStatus(DeliveryStatus.FAILED);
            notificationRepository.save(notification);
        }

        log.info("Created notification {} for user {}", notification.getId(), recipientUserId);

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
            notification.setEventType(data.eventType());
            notification.setEventDetails(data.eventDetails());
            notification.setLink(data.link());
            notification.setCreatedAt(now);
            notification.setRead(false);
            notification.setDeliveryStatus(DeliveryStatus.PENDING);

            // Get user email for notification
            var email = userRepository.findEmailByUsername(data.recipientUserId());
            if (email.isPresent()) {
                notification.setRecipientEmail(email.get());
            }

            notifications.add(notification);
        }

        List<Notification> savedNotifications = notificationRepository.saveAll(notifications);

        // Send emails for all notifications (asynchronously)
        for (Notification notification : savedNotifications) {
            try {
                if (notification.getRecipientEmail() != null) {
                    var status = emailService.sendNotificationEmail(notification);
                    notification.updateDeliveryStatus(status);
                }
            } catch (Exception e) {
                emailService.logDeliveryFailure(
                        notification.getRecipientEmail(),
                        notification.getEventType().toString(),
                        "Failed to send email: " + e.getMessage());
                notification.updateDeliveryStatus(DeliveryStatus.FAILED);
            }
        }

        // Save updated delivery statuses
        notificationRepository.saveAll(savedNotifications);

        log.info("Created {} notifications in batch", savedNotifications.size());

        return savedNotifications.stream().map(notificationMapper::toDto).toList();
    }

    /**
     * Data class for batch notification creation
     */
    public record NotificationData(
            String recipientUserId, NotificationEventType eventType, String eventDetails, String link) {}

    /**
     * Get notifications for a user with pagination
     */
    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotificationsForUser(String recipientUserId, Pageable pageable) {
        Page<Notification> notifications =
                notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, pageable);
        return notifications.map(notificationMapper::toDto);
    }

    /**
     * Mark notification as read
     * Only the recipient can mark their own notifications as read
     */
    @Transactional
    public NotificationDto markAsRead(UUID notificationId, String recipientUserId) {
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

        return notificationMapper.toDto(notification);
    }

    /**
     * Mark notification as unread
     * Only the recipient can mark their own notifications as unread
     */
    @Transactional
    public NotificationDto markAsUnread(UUID notificationId, String recipientUserId) {
        int updated = notificationRepository.markAsUnread(notificationId, recipientUserId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Notification not found or access denied");
        }

        // Fetch updated notification
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        log.info("Marked notification {} as unread for user {}", notificationId, recipientUserId);

        return notificationMapper.toDto(notification);
    }

    /**
     * Mark notification as read via email tracking (public endpoint)
     * This is idempotent - subsequent calls don't update the timestamp
     * @return true if notification was found and processed, false if not found
     */
    @Transactional
    public boolean markAsReadByTracking(UUID notificationId) {
        var notificationOpt = notificationRepository.findById(notificationId);

        if (notificationOpt.isEmpty()) {
            return false;
        }

        Notification notification = notificationOpt.get();

        // Only mark as read if not already read (idempotent behavior)
        if (!notification.getRead()) {
            notification.markAsRead();
            notificationRepository.save(notification);
            log.info("Marked notification {} as read via email tracking", notificationId);
        } else {
            log.debug("Notification {} already marked as read, skipping update", notificationId);
        }

        return true;
    }
}
