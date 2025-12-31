package com.sivalabs.ft.features.notifications;

import com.sivalabs.ft.features.domain.entities.Notification;
import com.sivalabs.ft.features.domain.models.DeliveryStatus;

public interface EmailService {

    /**
     * Send notification email to recipient
     * @param notification The notification to send
     * @return Delivery status indicating success or failure
     */
    DeliveryStatus sendNotificationEmail(Notification notification);

    /**
     * Log email delivery failure
     * @param recipientEmail Email address that failed
     * @param eventType Type of event
     * @param errorDetails Error details
     */
    void logDeliveryFailure(String recipientEmail, String eventType, String errorDetails);
}
