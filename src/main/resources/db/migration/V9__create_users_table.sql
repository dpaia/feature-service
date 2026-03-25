CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL
);

COMMENT ON TABLE users IS 'Stores user accounts for email notification delivery';
COMMENT ON COLUMN users.username IS 'Unique username used as recipient_user_id in notifications';
COMMENT ON COLUMN users.email IS 'Email address for sending notifications';
