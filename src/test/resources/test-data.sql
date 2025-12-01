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

insert into features (id, product_id, release_id, code, title, description, status, created_by, assigned_to, created_at, planned_completion_date, planning_status, feature_owner, notes, blockage_reason) values
(1, 1, 1, 'IDEA-1', 'Redesign Structure Tool Window', 'Redesign Structure Tool Window to show logical structure', 'NEW', 'otheruser', 'otheruser', '2024-02-24', '2024-03-01', 'NOT_STARTED', 'otheruser', 'Initial notes', null),
(2, 1, 1, 'IDEA-2', 'SDJ Repository Method AutoCompletion', 'Spring Data JPA Repository Method AutoCompletion as you type', 'NEW', 'otheruser', 'otheruser', '2024-03-14', '2024-04-01', 'NOT_STARTED', 'otheruser', 'Initial notes', null),
(3, 2, null, 'GO-3', 'Make Go to Type and Go to Symbol dumb aware', 'Make Go to Type and Go to Symbol dumb aware', 'IN_PROGRESS', 'antonarhipov', 'andreybelyaev', '2024-01-14', null, 'NOT_STARTED', null, null, null),
(4, 1, null, 'IDEA-3', 'New unassigned feature 1', 'This feature is not assigned to any release yet', 'NEW', 'user', 'user', '2024-02-25', null, 'NOT_STARTED', null, null, null),
(5, 1, null, 'IDEA-4', 'New unassigned feature 2', 'This feature is not assigned to any release yet', 'NEW', 'user', 'user', '2024-02-26', null, 'NOT_STARTED', null, null, null),
(6, 1, null, 'IDEA-5', 'New unassigned feature 3', 'This feature is not assigned to any release yet', 'NEW', 'user', 'user', '2024-02-27', null, 'NOT_STARTED', null, null, null),
(7, 1, null, 'IDEA-6', 'New unassigned feature 4', 'This feature is not assigned to any release yet', 'NEW', 'user', 'user', '2024-02-28', null, 'NOT_STARTED', null, null, null),
(8, 1, null, 'IDEA-7', 'New unassigned feature 5', 'This feature is not assigned to any release yet', 'NEW', 'user', 'user', '2024-03-01', null, 'NOT_STARTED', null, null, null),
(9, 1, null, 'IDEA-8', 'New unassigned feature 6', 'This feature is not assigned to any release yet', 'NEW', 'user', 'user', '2024-03-02', null, 'NOT_STARTED', null, null, null)
;

insert into favorite_features (id, feature_id, user_id) values
(1, 2, 'user');

insert into comments (id, feature_id, created_by, content) values
(1, 1, 'user', 'This is a comment on feature IDEA-1'),
(2,  1, 'user', 'This is a comment on feature IDEA-2'),
(3, 1, 'user', 'This is a comment on feature GO-3');