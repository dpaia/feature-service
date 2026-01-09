package com.sivalabs.ft.features.domain;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for logging email delivery failures
 */
@Service
public class EmailDeliveryLogger {

    private static final Logger logger = LoggerFactory.getLogger(EmailDeliveryLogger.class);

    /**
     * Log email delivery failure with details
     * @param recipientEmail the email address that failed to receive the notification
     * @param eventType the type of event that triggered the notification
     * @param errorDetails the error details explaining why delivery failed
     */
    public void logDeliveryFailure(String recipientEmail, String eventType, String errorDetails) {
        Instant timestamp = Instant.now();

        // Log to application logs
        logger.error(
                "Email delivery failed - Recipient: {}, Event Type: {}, Timestamp: {}, Error: {}",
                recipientEmail,
                eventType,
                timestamp,
                errorDetails);

        // TODO: In a production system, you might want to:
        // 1. Store failures in a dedicated database table for monitoring
        // 2. Send alerts to administrators
        // 3. Integrate with monitoring systems (e.g., Prometheus, DataDog)
        // 4. Implement retry mechanisms

        // For now, we'll use structured logging which can be picked up by log aggregation systems
        logger.warn(
                "EMAIL_DELIVERY_FAILURE recipient={} eventType={} timestamp={} error={}",
                recipientEmail,
                eventType,
                timestamp,
                errorDetails);
    }

    /**
     * Log successful email delivery
     * @param recipientEmail the email address that successfully received the notification
     * @param eventType the type of event that triggered the notification
     */
    public void logDeliverySuccess(String recipientEmail, String eventType) {
        Instant timestamp = Instant.now();

        logger.info(
                "Email delivery successful - Recipient: {}, Event Type: {}, Timestamp: {}",
                recipientEmail,
                eventType,
                timestamp);

        // Structured logging for monitoring
        logger.info(
                "EMAIL_DELIVERY_SUCCESS recipient={} eventType={} timestamp={}", recipientEmail, eventType, timestamp);
    }
}
