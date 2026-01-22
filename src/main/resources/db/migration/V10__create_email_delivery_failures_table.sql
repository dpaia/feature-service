CREATE TABLE email_delivery_failures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    error_message TEXT NOT NULL,
    failed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_delivery_failures_notification
        FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE
);

CREATE INDEX idx_email_delivery_failures_notification_id ON email_delivery_failures(notification_id);
CREATE INDEX idx_email_delivery_failures_failed_at ON email_delivery_failures(failed_at DESC);
CREATE INDEX idx_email_delivery_failures_recipient_email ON email_delivery_failures(recipient_email);