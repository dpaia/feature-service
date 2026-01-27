package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.entities.Notification;
import com.sivalabs.ft.features.domain.models.DeliveryStatus;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * Service for sending email notifications.
 * Handles email rendering, error handling, and logging.
 * Email delivery failures are logged but do not throw exceptions.
 */
@Service
public class NotificationEmailService {
    private static final Logger log = LoggerFactory.getLogger(NotificationEmailService.class);

    private final JavaMailSender mailSender;
    private final ApplicationProperties applicationProperties;
    private final NotificationRepository notificationRepository;
    private final EmailDeliveryFailureService emailDeliveryFailureService;

    public NotificationEmailService(
            JavaMailSender mailSender,
            ApplicationProperties applicationProperties,
            NotificationRepository notificationRepository,
            EmailDeliveryFailureService emailDeliveryFailureService) {
        this.mailSender = mailSender;
        this.applicationProperties = applicationProperties;
        this.notificationRepository = notificationRepository;
        this.emailDeliveryFailureService = emailDeliveryFailureService;
    }

    /**
     * Send email notification for a saved notification.
     * On error: logs recipient, eventType, timestamp, and error details, and updates delivery_status to FAILED.
     * Does NOT throw exception to avoid affecting notification creation.
     * Uses REQUIRES_NEW to create a new transaction (notification entity is detached after original commit).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNotificationEmail(Notification notification) {
        if (notification.getRecipientEmail() == null
                || notification.getRecipientEmail().isBlank()) {
            log.debug("Skipping email send for notification {} - no recipient email", notification.getId());
            return;
        }

        try {
            String subject = buildSubject(notification);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(notification.getRecipientEmail());
            helper.setSubject(subject);
            helper.setText(buildHtmlContent(notification, subject), true);
            helper.setFrom(applicationProperties.mailFrom());

            mailSender.send(message);
            log.info(
                    "Email sent successfully to {} for notification {}",
                    notification.getRecipientEmail(),
                    notification.getId());

        } catch (Exception e) {
            logEmailFailure(notification, e);
            persistEmailFailure(notification, e);
            updateDeliveryStatusToFailed(notification);
        }
    }

    private void updateDeliveryStatusToFailed(Notification notification) {
        try {
            notificationRepository.updateDeliveryStatus(notification.getId(), DeliveryStatus.FAILED);
            log.debug("Updated delivery_status to FAILED for notification {}", notification.getId());
        } catch (Exception e) {
            log.warn("Failed to update delivery_status for notification {}", notification.getId(), e);
        }
    }

    private String buildSubject(Notification notification) {
        return switch (notification.getEventType()) {
            case FEATURE_CREATED -> "New Feature Created";
            case FEATURE_UPDATED -> "Feature Updated";
            case FEATURE_DELETED -> "Feature Deleted";
            case RELEASE_CREATED -> "New Release Created";
            case RELEASE_UPDATED -> "Release Updated";
            case RELEASE_DELETED -> "Release Deleted";
        };
    }

    private String buildHtmlContent(Notification notification, String subject) {
        String trackingPixelUrl = buildTrackingPixelUrl(notification);
        String linkHtml = notification.getLink() != null
                ? "<p><a href=\"" + HtmlUtils.htmlEscape(notification.getLink()) + "\">View Details</a></p>"
                : "";

        String eventDetails = notification.getEventDetails() != null
                ? HtmlUtils.htmlEscape(notification.getEventDetails())
                : "No details available";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                </head>
                <body>
                    <h2>%s</h2>
                    <p>%s</p>
                    %s
                    <hr>
                    <p style="color: #666; font-size: 12px;">
                        Recipient: %s<br>
                        Event triggered at: %s
                    </p>
                    <img src="%s" width="1" height="1" alt="" style="display:none;">
                </body>
                </html>
                """
                .formatted(
                        subject,
                        eventDetails,
                        linkHtml,
                        HtmlUtils.htmlEscape(notification.getRecipientUserId()),
                        HtmlUtils.htmlEscape(String.valueOf(notification.getCreatedAt())),
                        HtmlUtils.htmlEscape(trackingPixelUrl));
    }

    private String buildTrackingPixelUrl(Notification notification) {
        String baseUrl = applicationProperties.publicBaseUrl();
        // Remove trailing slash if present
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/notifications/" + notification.getId() + "/read";
    }

    private void logEmailFailure(Notification notification, Exception e) {
        log.error(
                "Email delivery failed - recipient: {}, eventType: {}, timestamp: {}, error: {}",
                notification.getRecipientEmail(),
                notification.getEventType(),
                Instant.now(),
                e.getMessage(),
                e);
    }

    private void persistEmailFailure(Notification notification, Exception e) {
        try {
            emailDeliveryFailureService.logFailure(
                    notification.getId(),
                    notification.getRecipientEmail(),
                    notification.getEventType().name(),
                    e.getMessage());
        } catch (Exception ex) {
            // Must not affect the flow - commit-time exceptions from REQUIRES_NEW can escape
            log.warn("Failed to persist email delivery failure: {}", ex.getMessage());
        }
    }
}
