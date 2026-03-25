ALTER TABLE notifications ADD COLUMN recipient_email VARCHAR(255);

COMMENT ON COLUMN notifications.recipient_email IS 'Email address of the recipient, denormalized from users table for delivery tracking';
