-- Create notifications table for user notification system
-- Supports both in-app and email notifications with delivery tracking

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN ('FEATURE_CREATED', 'FEATURE_UPDATED', 'FEATURE_DELETED', 'RELEASE_CREATED', 'RELEASE_UPDATED', 'RELEASE_DELETED')),
    event_details TEXT,
    link VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    delivery_status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (delivery_status IN ('PENDING', 'DELIVERED', 'FAILED'))
);

-- Create indexes for efficient queries
CREATE INDEX idx_notifications_recipient_user_id ON notifications(recipient_user_id);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);
CREATE INDEX idx_notifications_read ON notifications(read);
CREATE INDEX idx_notifications_delivery_status ON notifications(delivery_status);

-- Create composite index for user's unread notifications (most common query)
CREATE INDEX idx_notifications_recipient_unread ON notifications(recipient_user_id, read, created_at DESC);

-- Add comments for documentation
COMMENT ON TABLE notifications IS 'Stores user notifications for features and releases with delivery tracking';
COMMENT ON COLUMN notifications.id IS 'Unique notification identifier (UUID)';
COMMENT ON COLUMN notifications.recipient_user_id IS 'User ID who should receive this notification';
COMMENT ON COLUMN notifications.event_type IS 'Type of event that triggered the notification';
COMMENT ON COLUMN notifications.event_details IS 'JSON or text payload with structured event details';
COMMENT ON COLUMN notifications.link IS 'URL link to the relevant feature/release';
COMMENT ON COLUMN notifications.created_at IS 'When the notification was created';
COMMENT ON COLUMN notifications.read IS 'Whether the notification has been read by the user';
COMMENT ON COLUMN notifications.read_at IS 'When the notification was marked as read';
COMMENT ON COLUMN notifications.delivery_status IS 'Email delivery status: PENDING, DELIVERED, or FAILED';