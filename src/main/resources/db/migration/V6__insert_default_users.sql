-- Password: admin123 (BCrypt encoded)
INSERT INTO users (username, password, account_non_expired, account_non_locked, credentials_non_expired, enabled)
VALUES ('admin', '$2a$10$REzSx0w4StPQ7lXMb.Ki6OpSTHY5blkPs9Cqv8.7l87mC7lzwMDJq', true, true, true, true);

-- Password: user123 (BCrypt encoded)
INSERT INTO users (username, password, account_non_expired, account_non_locked, credentials_non_expired, enabled)
VALUES ('user', '$2a$10$fkCIJsZQtul4y6y0bylRd.4g1MBD0jA/zdLF699ExWKfkjtEP61zi', true, true, true, true);

-- Add roles for admin user
INSERT INTO user_roles (user_id, role) 
VALUES 
    ((SELECT id FROM users WHERE username = 'admin'), 'USER'),
    ((SELECT id FROM users WHERE username = 'admin'), 'ADMIN');

-- Add roles for regular user
INSERT INTO user_roles (user_id, role) 
VALUES 
    ((SELECT id FROM users WHERE username = 'user'), 'USER');
