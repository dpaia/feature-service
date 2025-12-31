package com.sivalabs.ft.features.notifications;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.entities.Notification;
import com.sivalabs.ft.features.domain.models.DeliveryStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final ApplicationProperties applicationProperties;

    public EmailServiceImpl(JavaMailSender mailSender, ApplicationProperties applicationProperties) {
        this.mailSender = mailSender;
        this.applicationProperties = applicationProperties;
    }

    @Override
    public DeliveryStatus sendNotificationEmail(Notification notification) {
        // Check if email sending is enabled
        if (applicationProperties.email() == null
                || !applicationProperties.email().isEnabled()) {
            logger.debug("Email sending is disabled, skipping email for notification {}", notification.getId());
            return DeliveryStatus.PENDING; // Return PENDING instead of DELIVERED when disabled
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(notification.getRecipientEmail());
            helper.setSubject(buildEmailSubject(notification));
            helper.setText(buildEmailContent(notification), true);
            helper.setFrom("noreply@featureservice.com");

            mailSender.send(message);
            logger.info(
                    "Email sent successfully to {} for notification {}",
                    notification.getRecipientEmail(),
                    notification.getId());

            return DeliveryStatus.DELIVERED;

        } catch (MessagingException | MailException e) {
            String errorDetails = "Failed to send email: " + e.getMessage();
            logDeliveryFailure(
                    notification.getRecipientEmail(),
                    notification.getEventType().toString(),
                    errorDetails);
            return DeliveryStatus.FAILED;
        }
    }

    @Override
    public void logDeliveryFailure(String recipientEmail, String eventType, String errorDetails) {
        logger.error(
                "Email delivery failed - Recipient: {}, Event Type: {}, Timestamp: {}, Error: {}",
                recipientEmail,
                eventType,
                Instant.now(),
                errorDetails);
    }

    private String buildEmailSubject(Notification notification) {
        return switch (notification.getEventType()) {
            case FEATURE_CREATED -> "New Feature Created";
            case FEATURE_UPDATED -> "Feature Updated";
            case FEATURE_DELETED -> "Feature Deleted";
            case RELEASE_CREATED -> "New Release Created";
            case RELEASE_UPDATED -> "Release Updated";
            case RELEASE_DELETED -> "Release Deleted";
        };
    }

    private String buildEmailContent(Notification notification) {
        String trackingPixelUrl = buildTrackingPixelUrl(notification.getId().toString());

        StringBuilder content = new StringBuilder();
        content.append("<!DOCTYPE html>");
        content.append("<html>");
        content.append("<head><meta charset=\"UTF-8\"></head>");
        content.append("<body>");
        content.append("<h2>").append(buildEmailSubject(notification)).append("</h2>");

        // Event details
        if (notification.getEventDetails() != null) {
            content.append("<p><strong>Event Summary:</strong></p>");
            content.append("<p>").append(notification.getEventDetails()).append("</p>");
        }

        // Actor information (extracted from event details if available)
        content.append("<p><strong>Triggered by:</strong> ")
                .append(extractActorFromDetails(notification))
                .append("</p>");

        // Link to affected entity
        if (notification.getLink() != null) {
            content.append("<p><a href=\"").append(notification.getLink()).append("\">View Details</a></p>");
        }

        // Tracking pixel (1x1 transparent GIF)
        content.append("<img src=\"")
                .append(trackingPixelUrl)
                .append("\" width=\"1\" height=\"1\" style=\"display:none;\" />");

        content.append("</body>");
        content.append("</html>");

        return content.toString();
    }

    private String buildTrackingPixelUrl(String notificationId) {
        String baseUrl = applicationProperties.publicBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/notifications/" + notificationId + "/read";
    }

    private String extractActorFromDetails(Notification notification) {
        // Simple extraction - in a real implementation, this would parse JSON event details
        String details = notification.getEventDetails();
        if (details != null && details.contains("actor")) {
            // This is a simplified extraction - would need proper JSON parsing
            return "System User";
        }
        return "System";
    }
}
