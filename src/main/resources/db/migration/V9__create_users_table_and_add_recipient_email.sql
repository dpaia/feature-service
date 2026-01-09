-- Create users table for email notifications
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL
);

-- Create index for efficient username lookups
CREATE INDEX idx_users_username ON users(username);

-- Insert sample users with email addresses
INSERT INTO users (username, email) VALUES
('admin', 'admin@example.com'),
('siva', 'siva@example.com'),
('daniiltsarev', 'daniiltsarev@example.com'),
('antonarhipov', 'antonarhipov@example.com'),
('marcobehler', 'marcobehler@example.com'),
('andreybelyaev', 'andreybelyaev@example.com');

-- Add recipient_email column to notifications table
ALTER TABLE notifications ADD COLUMN recipient_email VARCHAR(255);

-- Add comments for documentation
COMMENT ON TABLE users IS 'Stores user information including email addresses for notifications';
COMMENT ON COLUMN users.id IS 'Unique user identifier (UUID)';
COMMENT ON COLUMN users.username IS 'Unique username for the user';
COMMENT ON COLUMN users.email IS 'Email address for sending notifications';
COMMENT ON COLUMN notifications.recipient_email IS 'Email address where notification should be sent (retrieved from users table)';