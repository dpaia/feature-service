package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.config.EmailProperties;
import com.sivalabs.ft.features.domain.entities.Notification;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Implementation of EmailService for sending notification emails
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Value("${publicBaseUrl}")
    private String publicBaseUrl;

    private final EmailDeliveryLogger emailDeliveryLogger;
    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    public EmailServiceImpl(
            EmailDeliveryLogger emailDeliveryLogger, JavaMailSender mailSender, EmailProperties emailProperties) {
        this.emailDeliveryLogger = emailDeliveryLogger;
        this.mailSender = mailSender;
        this.emailProperties = emailProperties;
    }

    @Override
    public boolean sendNotificationEmail(Notification notification) {
        if (!emailProperties.isEnabled()) {
            logger.debug("Email sending is disabled, skipping notification {}", notification.getId());
            return true; // Return true to not block notification creation
        }

        try {
            if (notification.getRecipientEmail() == null
                    || notification.getRecipientEmail().trim().isEmpty()) {
                logger.warn(
                        "Cannot send email notification {}: recipient email is null or empty", notification.getId());
                return false; // Don't log as delivery failure - this is expected for users without email
            }

            String trackingPixelUrl =
                    generateTrackingPixelUrl(notification.getId().toString());
            String emailContent = buildEmailContent(notification, trackingPixelUrl);

            logger.info("Sending email notification {} to {}", notification.getId(), notification.getRecipientEmail());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailProperties.getFrom());
            helper.setTo(notification.getRecipientEmail());
            helper.setSubject(getEmailSubject(notification));
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info(
                    "Email notification {} sent successfully to {}",
                    notification.getId(),
                    notification.getRecipientEmail());

            return true;

        } catch (MessagingException e) {
            logger.error(
                    "Failed to send email notification {} to {}",
                    notification.getId(),
                    notification.getRecipientEmail(),
                    e);
            emailDeliveryLogger.logDeliveryFailure(
                    notification.getRecipientEmail(),
                    notification.getEventType().toString(),
                    "MessagingException: " + e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error(
                    "Unexpected error sending email notification {} to {}",
                    notification.getId(),
                    notification.getRecipientEmail(),
                    e);
            emailDeliveryLogger.logDeliveryFailure(
                    notification.getRecipientEmail(),
                    notification.getEventType().toString(),
                    "Unexpected error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String generateTrackingPixelUrl(String notificationId) {
        return publicBaseUrl + "/api/notifications/" + notificationId + "/read";
    }

    private String getEmailSubject(Notification notification) {
        return switch (notification.getEventType()) {
            case FEATURE_CREATED -> "New Feature Created";
            case FEATURE_UPDATED -> "Feature Updated";
            case FEATURE_DELETED -> "Feature Deleted";
            case RELEASE_UPDATED -> "Release Updated";
            default -> "Feature Service Notification";
        };
    }

    private String buildEmailContent(Notification notification, String trackingPixelUrl) {
        StringBuilder content = new StringBuilder();
        content.append("<!DOCTYPE html>");
        content.append("<html>");
        content.append("<head>");
        content.append("<meta charset=\"UTF-8\">");
        content.append("<title>Feature Service Notification</title>");
        content.append("</head>");
        content.append("<body style=\"font-family: Arial, sans-serif; line-height: 1.6; color: #333;\">");
        content.append("<div style=\"max-width: 600px; margin: 0 auto; padding: 20px;\">");

        content.append("<h2 style=\"color: #2c3e50;\">Feature Service Notification</h2>");

        content.append("<div style=\"background-color: #f8f9fa; padding: 15px; border-radius: 5px; margin: 20px 0;\">");
        content.append("<h3 style=\"margin-top: 0; color: #495057;\">")
                .append(getEmailSubject(notification))
                .append("</h3>");

        if (notification.getEventDetails() != null) {
            content.append("<p><strong>Event Details:</strong> ")
                    .append(notification.getEventDetails())
                    .append("</p>");
        }

        if (notification.getLink() != null) {
            content.append("<p><a href=\"")
                    .append(notification.getLink())
                    .append("\" style=\"color: #007bff;\">View Details</a></p>");
        }

        content.append("</div>");

        content.append("<p style=\"color: #6c757d; font-size: 12px;\">");
        content.append("This is an automated notification from Feature Service. ");
        content.append("Please do not reply to this email.");
        content.append("</p>");

        // Add tracking pixel
        content.append("<!-- Tracking Pixel -->");
        content.append("<img src=\"")
                .append(trackingPixelUrl)
                .append("\" width=\"1\" height=\"1\" style=\"display: none;\" alt=\"\">");

        content.append("</div>");
        content.append("</body>");
        content.append("</html>");

        return content.toString();
    }
}
