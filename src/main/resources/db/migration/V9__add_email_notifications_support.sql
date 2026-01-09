-- Create users table for storing user information
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL
);

-- Add index for username lookups
CREATE INDEX idx_users_username ON users(username);

-- Insert sample users (populated by support team)
INSERT INTO users (username, email) VALUES
    ('admin', 'admin@company.com'),
    ('siva', 'siva@company.com'),
    ('bob', 'bob@company.com'),
    ('alice', 'alice@company.com'),
    ('testuser', 'testuser@company.com'),
    ('creator', 'creator@company.com'),
    ('assignee', 'assignee@company.com'),
    ('recipient', 'recipient@company.com'),
    ('user1', 'user1@company.com'),
    ('user2', 'user2@company.com'),
    ('marcobehler', 'marcobehler@company.com'),
    ('daniiltsarev', 'daniiltsarev@company.com'),
    ('antonarhipov', 'antonarhipov@company.com'),
    ('andreybelyaev', 'andreybelyaev@company.com'),
    ('developer', 'developer@company.com'),
    ('releaseManager', 'releaseManager@company.com'),
    ('productOwner', 'productOwner@company.com'),
    ('userA', 'userA@company.com'),
    ('userB', 'userB@company.com'),
    ('userC', 'userC@company.com');

-- Add recipient_email column to notifications table for email delivery
ALTER TABLE notifications ADD COLUMN recipient_email VARCHAR(255);

COMMENT ON COLUMN notifications.recipient_email IS 'Email address of the notification recipient for email delivery';


