delete from favorite_features;
delete from comments;
delete from features;
delete from releases;
delete from products;

insert into products (id, code, prefix, name, description, image_url, disabled, created_by, created_at) values
(1, 'intellij', 'IDEA', 'IntelliJ IDEA', 'JetBrains IDE for Java', 'https://resources.jetbrains.com/storage/products/company/brand/logos/IntelliJ_IDEA.png', false, 'admin', '2024-03-01 00:00:00'),
(2, 'goland','GO','GoLand', 'JetBrains IDE for Go', 'https://resources.jetbrains.com/storage/products/company/brand/logos/GoLand.png',false, 'admin','2024-03-01 00:00:00'),
(3, 'webstorm','WEB','WebStorm', 'JetBrains IDE for Web Development','https://resources.jetbrains.com/storage/products/company/brand/logos/WebStorm.png', false, 'admin','2024-03-01 00:00:00'),
(4, 'pycharm','PY','PyCharm', 'JetBrains IDE for Python', 'https://resources.jetbrains.com/storage/products/company/brand/logos/PyCharm.png',false, 'admin','2024-03-01 00:00:00'),
(5, 'rider','RIDER','Rider', 'JetBrains IDE for .NET', 'https://resources.jetbrains.com/storage/products/company/brand/logos/Rider.png',false, 'admin','2024-03-01 00:00:00')
;

insert into releases (id, product_id, code, description, status, created_by, created_at) values
(1, 1, 'IDEA-2023.3.8', 'IntelliJ IDEA 2023.3.8', 'RELEASED', 'admin','2023-03-25'),
(2, 1, 'IDEA-2024.2.3', 'IntelliJ IDEA 2024.2.4', 'RELEASED', 'admin','2024-02-25'),
(3, 2, 'GO-2024.2.3', 'GoLand 2024.2.4', 'RELEASED', 'admin','2024-02-15'),
(4, 3, 'WEB-2024.2.3', 'WebStorm 2024.2.4', 'RELEASED', 'admin','2024-02-20'),
(5, 4, 'PY-2024.2.3', 'PyCharm 2024.2.4', 'RELEASED', 'admin','2024-02-20'),
(6, 5, 'RIDER-2024.2.6', 'Rider 2024.2.6', 'RELEASED', 'admin','2024-02-16')
;

insert into features (id, product_id, release_id, code, title, description, status, created_by, assigned_to, created_at) values
(1, 1, 1, 'IDEA-1', 'Redesign Structure Tool Window', 'Redesign Structure Tool Window to show logical structure', 'NEW', 'siva', 'marcobehler', '2024-02-24'),
(2, 1, 1, 'IDEA-2', 'SDJ Repository Method AutoCompletion', 'Spring Data JPA Repository Method AutoCompletion as you type', 'NEW', 'daniiltsarev', 'siva', '2024-03-14'),
(3, 2, null, 'GO-3', 'Make Go to Type and Go to Symbol dumb aware', 'Make Go to Type and Go to Symbol dumb aware', 'IN_PROGRESS', 'antonarhipov', 'andreybelyaev', '2024-01-14')
;

insert into favorite_features (id, feature_id, user_id) values
(1, 2, 'user');

insert into comments (id, feature_id, created_by, content) values
(1, 1, 'user', 'This is a comment on feature IDEA-1'),
(2,  1, 'user', 'This is a comment on feature IDEA-2'),
(3, 1, 'user', 'This is a comment on feature GO-3');

-- Sample feature usage data for testing
INSERT INTO feature_usage (id, user_id, feature_code, product_code, action_type, timestamp, context, ip_address, user_agent)
VALUES
    (nextval('feature_usage_id_seq'), 'user1@example.com', 'IDEA-1', 'intellij', 'FEATURE_VIEWED', NOW() - INTERVAL '2 hours', '{"source": "web"}', '192.168.1.100', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user1@example.com', 'IDEA-2', 'intellij', 'FEATURE_VIEWED', NOW() - INTERVAL '1 hour', '{"source": "web"}', '192.168.1.100', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user2@example.com', 'IDEA-1', 'intellij', 'FEATURE_VIEWED', NOW() - INTERVAL '3 hours', '{"source": "mobile"}', '192.168.1.101', 'Mobile App'),
    (nextval('feature_usage_id_seq'), 'user2@example.com', 'GO-3', 'goland', 'FEATURE_CREATED', NOW() - INTERVAL '4 hours', '{"source": "web"}', '192.168.1.101', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user3@example.com', 'IDEA-1', 'intellij', 'FEATURE_UPDATED', NOW() - INTERVAL '5 hours', '{"source": "api"}', '192.168.1.102', 'API Client'),
    (nextval('feature_usage_id_seq'), 'user3@example.com', 'IDEA-2', 'intellij', 'FEATURE_DELETED', NOW() - INTERVAL '6 hours', '{"source": "web"}', '192.168.1.102', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user1@example.com', 'IDEA-1', 'intellij', 'FEATURES_LISTED', NOW() - INTERVAL '30 minutes', '{"source": "web"}', '192.168.1.100', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user2@example.com', NULL, 'intellij', 'PRODUCT_VIEWED', NOW() - INTERVAL '1 hour', '{"source": "web"}', '192.168.1.101', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user3@example.com', NULL, 'intellij', 'PRODUCT_CREATED', NOW() - INTERVAL '2 days', '{"source": "admin"}', '192.168.1.102', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user1@example.com', NULL, 'intellij', 'RELEASE_VIEWED', NOW() - INTERVAL '8 hours', '{"source": "web"}', '192.168.1.100', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user2@example.com', NULL, 'intellij', 'RELEASE_CREATED', NOW() - INTERVAL '1 day', '{"source": "admin"}', '192.168.1.101', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user3@example.com', 'IDEA-1', 'intellij', 'COMMENT_ADDED', NOW() - INTERVAL '10 hours', '{"source": "web"}', '192.168.1.102', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user1@example.com', 'IDEA-2', 'intellij', 'FAVORITE_ADDED', NOW() - INTERVAL '12 hours', '{"source": "web"}', '192.168.1.100', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user2@example.com', 'GO-3', 'goland', 'FAVORITE_REMOVED', NOW() - INTERVAL '15 hours', '{"source": "web"}', '192.168.1.101', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user3@example.com', 'IDEA-1', 'intellij', 'FEATURE_VIEWED', NOW() - INTERVAL '20 minutes', '{"source": "web"}', '192.168.1.102', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user4@example.com', 'IDEA-2', 'intellij', 'FEATURE_VIEWED', NOW() - INTERVAL '25 minutes', '{"source": "web"}', '192.168.1.103', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user4@example.com', 'GO-3', 'goland', 'FEATURE_VIEWED', NOW() - INTERVAL '35 minutes', '{"source": "mobile"}', '192.168.1.103', 'Mobile App'),
    (nextval('feature_usage_id_seq'), 'user5@example.com', 'IDEA-1', 'intellij', 'FEATURE_CREATED', NOW() - INTERVAL '40 minutes', '{"source": "api"}', '192.168.1.104', 'API Client'),
    (nextval('feature_usage_id_seq'), 'user5@example.com', 'IDEA-2', 'intellij', 'FEATURE_UPDATED', NOW() - INTERVAL '50 minutes', '{"source": "web"}', '192.168.1.104', 'Mozilla/5.0'),
    (nextval('feature_usage_id_seq'), 'user1@example.com', 'GO-3', 'goland', 'FEATURE_VIEWED', NOW() - INTERVAL '1 minute', '{"source": "web"}', '192.168.1.100', 'Mozilla/5.0');