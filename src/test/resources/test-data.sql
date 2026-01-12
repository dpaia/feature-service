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

insert into releases (id, product_id, code, description, status, planned_start_date, planned_release_date, actual_release_date, owner, notes, created_by, created_at, updated_by, updated_at) values
(1, 1, 'IDEA-2023.3.8', 'IntelliJ IDEA 2023.3.8 - Bug fixes and performance improvements', 'RELEASED', '2023-10-01 09:00:00', '2023-11-15 17:00:00', '2023-12-01 10:00:00', 'jane.smith', 'Stable release with critical bug fixes', 'admin','2023-03-25 00:00:00', 'jane.smith', '2023-12-01 10:30:00'),
(2, 1, 'IDEA-2024.2.3', 'IntelliJ IDEA 2024.2.4 - New features and improvements', 'RELEASED', '2024-01-10 09:00:00', '2024-02-20 17:00:00', '2024-02-25 14:00:00', 'john.doe', 'Major release with new AI-powered features', 'admin','2024-02-25 00:00:00', 'john.doe', '2024-02-25 14:30:00'),
(3, 2, 'GO-2024.2.3', 'GoLand 2024.2.4 - Enhanced tooling', 'RELEASED', '2024-01-05 09:00:00', '2024-02-10 17:00:00', '2024-02-15 11:00:00', 'jane.smith', 'Enhanced debugging and profiling tools', 'admin','2024-02-15 00:00:00', 'jane.smith', '2024-02-15 11:30:00'),
(4, 3, 'WEB-2024.2.3', 'WebStorm 2024.2.4 - Web development tools', 'RELEASED', '2024-01-08 09:00:00', '2024-02-15 17:00:00', '2024-02-20 09:00:00', 'jane.smith', 'Improved TypeScript and React support', 'admin','2024-02-20 00:00:00', 'jane.smith', '2024-02-20 09:30:00'),
(5, 4, 'PY-2024.2.3', 'PyCharm 2024.2.4 - Python development', 'RELEASED', '2024-01-12 09:00:00', '2024-02-18 17:00:00', '2024-02-20 13:00:00', 'jane.smith', 'Enhanced data science and ML support', 'admin','2024-02-20 00:00:00', 'jane.smith', '2024-02-20 13:30:00'),
(6, 5, 'RIDER-2024.2.6', 'Rider 2024.2.6 - .NET development', 'RELEASED', '2024-01-14 09:00:00', '2024-02-20 17:00:00', '2024-02-16 15:00:00', 'jane.smith', 'Improved C# and .NET framework support', 'admin','2024-02-16 00:00:00', 'jane.smith', '2024-02-16 15:30:00')
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