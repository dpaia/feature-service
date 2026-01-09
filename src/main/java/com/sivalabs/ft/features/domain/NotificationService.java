package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.NotificationDto;
import com.sivalabs.ft.features.domain.entities.Notification;
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

        // Look up user email and set recipient_email
        userRepository
                .findByUsername(recipientUserId)
                .ifPresent(user -> notification.setRecipientEmail(user.getEmail()));

        var savedNotification = notificationRepository.save(notification);

        log.info("Created notification {} for user {}", savedNotification.getId(), recipientUserId);

        // Send email notification asynchronously (don't let email failures prevent notification creation)
        sendEmailNotificationAsync(savedNotification);

        return notificationMapper.toDto(savedNotification);
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

            // Look up user email and set recipient_email
            userRepository
                    .findByUsername(data.recipientUserId())
                    .ifPresent(user -> notification.setRecipientEmail(user.getEmail()));

            notifications.add(notification);
        }

        List<Notification> savedNotifications = notificationRepository.saveAll(notifications);

        log.info("Created {} notifications in batch", savedNotifications.size());

        // Send email notifications asynchronously for all notifications
        for (Notification notification : savedNotifications) {
            sendEmailNotificationAsync(notification);
        }

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
     * Mark notification as read via email tracking pixel
     * This method is used for email read tracking and doesn't require user authentication
     * It's idempotent - subsequent calls won't update the timestamp
     */
    @Transactional
    public void markAsReadViaTracking(UUID notificationId) {
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        // Only mark as read if it's not already read (idempotent behavior)
        if (!notification.getRead()) {
            notification.markAsRead();
            notificationRepository.save(notification);
            log.info("Marked notification {} as read via email tracking", notificationId);
        } else {
            log.debug("Notification {} already marked as read, skipping update", notificationId);
        }
    }

    /**
     * Send email notification asynchronously
     * This method handles email sending and updates delivery status
     */
    private void sendEmailNotificationAsync(Notification notification) {
        if (notification.getRecipientEmail() == null
                || notification.getRecipientEmail().trim().isEmpty()) {
            log.warn("Cannot send email for notification {}: recipient email is null or empty", notification.getId());
            // Keep status as PENDING when email is missing - this is not a delivery failure
            return;
        }

        try {
            boolean emailSent = emailService.sendNotificationEmail(notification);
            DeliveryStatus status = emailSent ? DeliveryStatus.DELIVERED : DeliveryStatus.FAILED;
            updateDeliveryStatus(notification.getId(), status);

            if (emailSent) {
                log.info("Email sent successfully for notification {}", notification.getId());
            } else {
                log.warn("Failed to send email for notification {}", notification.getId());
            }
        } catch (Exception e) {
            log.error(
                    "Exception occurred while sending email for notification {}: {}",
                    notification.getId(),
                    e.getMessage(),
                    e);
            updateDeliveryStatus(notification.getId(), DeliveryStatus.FAILED);
        }
    }

    /**
     * Update delivery status of a notification
     */
    private void updateDeliveryStatus(UUID notificationId, DeliveryStatus status) {
        try {
            notificationRepository.findById(notificationId).ifPresent(notification -> {
                notification.updateDeliveryStatus(status);
                notificationRepository.save(notification);
            });
        } catch (Exception e) {
            log.error("Failed to update delivery status for notification {}: {}", notificationId, e.getMessage());
        }
    }
}
