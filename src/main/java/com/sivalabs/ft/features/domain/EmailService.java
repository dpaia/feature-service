package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Notification;

/**
 * Service for sending email notifications
 */
public interface EmailService {

    /**
     * Send email notification to the recipient
     * @param notification the notification to send via email
     * @return true if email was sent successfully, false otherwise
     */
    boolean sendNotificationEmail(Notification notification);

    /**
     * Generate tracking pixel URL for email read tracking
     * @param notificationId the notification ID
     * @return the tracking pixel URL
     */
    String generateTrackingPixelUrl(String notificationId);
}
