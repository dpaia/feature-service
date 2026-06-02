package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.dtos.NotificationDto;
import com.sivalabs.ft.features.domain.exceptions.NotificationNotDeliveredException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final ApplicationProperties applicationProperties;

    public EmailService(JavaMailSender mailSender, ApplicationProperties applicationProperties) {
        this.mailSender = mailSender;
        this.applicationProperties = applicationProperties;
    }

    /**
     * Send a notification email with a tracking pixel.
     * Throws {@link NotificationNotDeliveredException} on failure so the caller can update delivery status.
     */
    public void sendNotificationEmail(NotificationDto notification, String recipientEmail)
            throws NotificationNotDeliveredException {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(recipientEmail);
            helper.setSubject("Notification: " + notification.eventType().name().replace('_', ' '));

            String trackingUrl =
                    applicationProperties.publicBaseUrl() + "/api/notifications/" + notification.id() + "/read";
            String htmlBody = buildEmailBody(notification, trackingUrl);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Email sent to {} for notification {}", recipientEmail, notification.id());
        } catch (Exception e) {
            log.error(
                    "Failed to send email notification to {} for event {} at {}: {}",
                    recipientEmail,
                    notification.eventType(),
                    Instant.now(),
                    e.getMessage());
            throw new NotificationNotDeliveredException(
                    "Failure happened during sending notification with id {}".formatted(notification.id()));
        }
    }

    private String buildEmailBody(NotificationDto notification, String trackingUrl) {
        String eventSummary = notification.eventType().name().replace('_', ' ');
        String details = notification.eventDetails() != null ? notification.eventDetails() : "";
        String linkHtml = notification.link() != null
                ? "<p><a href=\""
                        + applicationProperties.publicBaseUrl()
                        + notification.link()
                        + "\">View Details</a></p>"
                : "";

        return """
                <html>
                <body>
                  <h2>%s</h2>
                  <pre>%s</pre>
                  %s
                  <img src="%s" width="1" height="1" style="display:none" alt="" />
                </body>
                </html>
                """
                .formatted(eventSummary, details, linkHtml, trackingUrl);
    }
}
