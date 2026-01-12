delete from favorite_features;
delete from comments;
delete from features;
delete from releases;
delete from products;

insert into products (id, code, prefix, name, description, image_url, disabled, created_by, created_at) values
(1, 'intellij', 'IDEA', 'IntelliJ IDEA', 'JetBrains IDE for Java', 'https://resources.jetbrains.com/storage/products/company/brand/logos/IntelliJ_IDEA.png', false, 'admin', '2024-03-01 00:00:00');

insert into releases (id, product_id, code, description, status, planned_start_date, planned_release_date, actual_release_date, owner, notes, created_by, created_at, updated_by, updated_at) values
(1, 1, 'IDEA-2024.5.0', 'Release with exactly 10% on-hold features', 'PLANNED', '2024-01-01 09:00:00', '2024-06-30 17:00:00', null, 'admin', 'Test release for risk level boundary', 'admin', '2024-01-01 00:00:00', 'admin', '2024-01-01 00:00:00');

-- 10 features with exactly 1 ON_HOLD = exactly 10% on-hold
insert into features (id, product_id, release_id, code, title, description, status, created_by, assigned_to, created_at) values
(1,  1, 1, 'IDEA-F1',  'Feature 1',  'Test Feature 1',  'ON_HOLD', 'admin', 'user', '2024-01-01'),
(2,  1, 1, 'IDEA-F2',  'Feature 2',  'Test Feature 2',  'NEW',     'admin', 'user', '2024-01-01'),
(3,  1, 1, 'IDEA-F3',  'Feature 3',  'Test Feature 3',  'NEW',     'admin', 'user', '2024-01-01'),
(4,  1, 1, 'IDEA-F4',  'Feature 4',  'Test Feature 4',  'NEW',     'admin', 'user', '2024-01-01'),
(5,  1, 1, 'IDEA-F5',  'Feature 5',  'Test Feature 5',  'NEW',     'admin', 'user', '2024-01-01'),
(6,  1, 1, 'IDEA-F6',  'Feature 6',  'Test Feature 6',  'NEW',     'admin', 'user', '2024-01-01'),
(7,  1, 1, 'IDEA-F7',  'Feature 7',  'Test Feature 7',  'NEW',     'admin', 'user', '2024-01-01'),
(8,  1, 1, 'IDEA-F8',  'Feature 8',  'Test Feature 8',  'NEW',     'admin', 'user', '2024-01-01'),
(9,  1, 1, 'IDEA-F9',  'Feature 9',  'Test Feature 9',  'NEW',     'admin', 'user', '2024-01-01'),
(10, 1, 1, 'IDEA-F10', 'Feature 10', 'Test Feature 10', 'NEW',     'admin', 'user', '2024-01-01');
