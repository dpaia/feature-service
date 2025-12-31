-- Create users table for email notifications
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for efficient queries
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

-- Add recipient_email column to notifications table
ALTER TABLE notifications ADD COLUMN recipient_email VARCHAR(255);

-- Add comments for documentation
COMMENT ON TABLE users IS 'Stores user information including email addresses for notifications';
COMMENT ON COLUMN users.id IS 'Unique user identifier (UUID)';
COMMENT ON COLUMN users.username IS 'Unique username for the user';
COMMENT ON COLUMN users.email IS 'Email address for sending notifications';
COMMENT ON COLUMN users.created_at IS 'When the user record was created';
COMMENT ON COLUMN users.updated_at IS 'When the user record was last updated';
COMMENT ON COLUMN notifications.recipient_email IS 'Email address where notification should be sent (retrieved from users table)';

-- Insert some sample users for testing
INSERT INTO users (username, email) VALUES 
    ('john.doe', 'john.doe@example.com'),
    ('jane.smith', 'jane.smith@example.com'),
    ('admin', 'admin@example.com');