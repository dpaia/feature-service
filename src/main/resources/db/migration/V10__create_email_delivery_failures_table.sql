-- Create email_delivery_failures table to store email delivery failure records
-- Used for admin troubleshooting and monitoring of failed email notifications

CREATE TABLE email_delivery_failures (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    error_message TEXT NOT NULL,
    failed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_email_failures_notification
        FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE
);

-- Create indexes for efficient queries
CREATE INDEX idx_email_failures_notification_id ON email_delivery_failures(notification_id);
CREATE INDEX idx_email_failures_failed_at ON email_delivery_failures(failed_at);

-- Add comments for documentation
COMMENT ON TABLE email_delivery_failures IS 'Stores email delivery failure records for admin review and troubleshooting';
COMMENT ON COLUMN email_delivery_failures.id IS 'Unique failure record identifier (UUID)';
COMMENT ON COLUMN email_delivery_failures.notification_id IS 'Reference to the notification that failed to send';
COMMENT ON COLUMN email_delivery_failures.recipient_email IS 'Email address the notification was attempted to send to';
COMMENT ON COLUMN email_delivery_failures.event_type IS 'Type of notification event that triggered the email';
COMMENT ON COLUMN email_delivery_failures.error_message IS 'Error message from the failed delivery attempt';
COMMENT ON COLUMN email_delivery_failures.failed_at IS 'Timestamp when the delivery failure occurred (UTC)';

