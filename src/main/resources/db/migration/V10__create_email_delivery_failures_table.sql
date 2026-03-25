CREATE TABLE email_delivery_failures (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID                     NOT NULL,
    recipient_email VARCHAR(255),
    event_type      VARCHAR(50),
    error_message   TEXT,
    failed_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_delivery_failures_notification_id ON email_delivery_failures (notification_id);
CREATE INDEX idx_email_delivery_failures_failed_at ON email_delivery_failures (failed_at DESC);
