package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.EmailDeliveryFailure;
import com.sivalabs.ft.features.domain.entities.Notification;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDeliveryFailureService {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryFailureService.class);

    private final EmailDeliveryFailureRepository emailDeliveryFailureRepository;

    public EmailDeliveryFailureService(EmailDeliveryFailureRepository emailDeliveryFailureRepository) {
        this.emailDeliveryFailureRepository = emailDeliveryFailureRepository;
    }

    /**
     * Save email delivery failure record.
     * Uses REQUIRES_NEW to ensure failure logging doesn't affect the main transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveEmailDeliveryFailure(
            UUID notificationId, String recipientEmail, NotificationEventType eventType, String errorMessage) {
        try {
            var failure = new EmailDeliveryFailure();
            failure.setNotificationId(notificationId);
            failure.setRecipientEmail(recipientEmail);
            failure.setEventType(eventType);
            failure.setErrorMessage(errorMessage);
            failure.setFailedAt(Instant.now());

            emailDeliveryFailureRepository.save(failure);

            log.debug(
                    "Saved email delivery failure for notification {} to recipient {}", notificationId, recipientEmail);
        } catch (Exception e) {
            // Log but don't throw - failure logging must not affect notification processing
            log.warn(
                    "Failed to save email delivery failure record for notification {}: {}",
                    notificationId,
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Save email delivery failure record from Notification entity.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveEmailDeliveryFailure(Notification notification, String errorMessage) {
        saveEmailDeliveryFailure(
                notification.getId(), notification.getRecipientEmail(), notification.getEventType(), errorMessage);
    }

    /**
     * Get all email delivery failures with pagination, ordered by failed_at DESC (newest first)
     */
    @Transactional(readOnly = true)
    public Page<EmailDeliveryFailure> getAllEmailDeliveryFailures(Pageable pageable) {
        return emailDeliveryFailureRepository.findAllOrderByFailedAtDesc(pageable);
    }

    /**
     * Get email delivery failures filtered by date with pagination, ordered by failed_at DESC (newest first)
     */
    @Transactional(readOnly = true)
    public Page<EmailDeliveryFailure> getEmailDeliveryFailuresByDate(LocalDate date, Pageable pageable) {
        // Convert LocalDate to Instant range (start of day to end of day in UTC)
        Instant startOfDay = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return emailDeliveryFailureRepository.findByDateOrderByFailedAtDesc(startOfDay, endOfDay, pageable);
    }

    /**
     * Get single email delivery failure by ID
     */
    @Transactional(readOnly = true)
    public EmailDeliveryFailure getEmailDeliveryFailureById(UUID id) {
        return emailDeliveryFailureRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Email delivery failure not found with id: " + id));
    }

    /**
     * Get all email delivery failures for a specific notification, ordered by failed_at DESC (newest first)
     */
    @Transactional(readOnly = true)
    public List<EmailDeliveryFailure> getEmailDeliveryFailuresByNotificationId(UUID notificationId) {
        return emailDeliveryFailureRepository.findByNotificationIdOrderByFailedAtDesc(notificationId);
    }
}
