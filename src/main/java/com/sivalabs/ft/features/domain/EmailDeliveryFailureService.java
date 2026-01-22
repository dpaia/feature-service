package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.EmailDeliveryFailureDto;
import com.sivalabs.ft.features.domain.entities.EmailDeliveryFailure;
import com.sivalabs.ft.features.domain.mappers.EmailDeliveryFailureMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing email delivery failure records.
 * Provides methods for logging failures and retrieving failure records for admin review.
 */
@Service
public class EmailDeliveryFailureService {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryFailureService.class);

    private final EmailDeliveryFailureRepository emailDeliveryFailureRepository;
    private final EmailDeliveryFailureMapper emailDeliveryFailureMapper;

    public EmailDeliveryFailureService(
            EmailDeliveryFailureRepository emailDeliveryFailureRepository,
            EmailDeliveryFailureMapper emailDeliveryFailureMapper) {
        this.emailDeliveryFailureRepository = emailDeliveryFailureRepository;
        this.emailDeliveryFailureMapper = emailDeliveryFailureMapper;
    }

    /**
     * Log an email delivery failure.
     * If saving fails, logs a warning and continues - failure logging must not interrupt notification flow.
     * Uses REQUIRES_NEW to run in a separate transaction:
     * - Failure record is saved even if outer transaction rolls back (valuable for diagnostics)
     * - Any exception here won't affect the outer transaction
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(UUID notificationId, String recipientEmail, String eventType, String errorMessage) {
        try {
            EmailDeliveryFailure failure = new EmailDeliveryFailure();
            failure.setId(UUID.randomUUID());
            failure.setNotificationId(notificationId);
            failure.setRecipientEmail(recipientEmail);
            failure.setEventType(eventType);
            failure.setErrorMessage(normalizeErrorMessage(errorMessage));
            failure.setFailedAt(Instant.now());

            emailDeliveryFailureRepository.save(failure);
            log.debug("Logged email delivery failure for notification {} to {}", notificationId, recipientEmail);
        } catch (Exception e) {
            log.warn(
                    "Failed to log email delivery failure for notification {} to {}: {}",
                    notificationId,
                    recipientEmail,
                    e.getMessage());
        }
    }

    private String normalizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Unknown error";
        }
        if (errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }
        return errorMessage;
    }

    /**
     * Get all failures with pagination, sorted by failed_at DESC (newest first).
     */
    @Transactional(readOnly = true)
    public Page<EmailDeliveryFailureDto> getAllFailures(Pageable pageable) {
        Page<EmailDeliveryFailure> failures = emailDeliveryFailureRepository.findAllByOrderByFailedAtDesc(pageable);
        return failures.map(emailDeliveryFailureMapper::toDto);
    }

    /**
     * Get failures for a specific date (UTC) with pagination, sorted by failed_at DESC.
     * Uses range queries for optimal index usage.
     */
    @Transactional(readOnly = true)
    public Page<EmailDeliveryFailureDto> getFailuresByDate(LocalDate date, Pageable pageable) {
        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Page<EmailDeliveryFailure> failures =
                emailDeliveryFailureRepository.findByFailedAtInRangeOrderByFailedAtDesc(start, end, pageable);
        return failures.map(emailDeliveryFailureMapper::toDto);
    }

    /**
     * Get a single failure by ID.
     */
    @Transactional(readOnly = true)
    public Optional<EmailDeliveryFailureDto> getFailureById(UUID id) {
        return emailDeliveryFailureRepository.findById(id).map(emailDeliveryFailureMapper::toDto);
    }

    /**
     * Get all failures for a specific notification, sorted by failed_at DESC.
     */
    @Transactional(readOnly = true)
    public List<EmailDeliveryFailureDto> getFailuresByNotificationId(UUID notificationId) {
        List<EmailDeliveryFailure> failures =
                emailDeliveryFailureRepository.findByNotificationIdOrderByFailedAtDesc(notificationId);
        return failures.stream().map(emailDeliveryFailureMapper::toDto).toList();
    }
}
