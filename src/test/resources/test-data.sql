create table if not exists milestones
(
    id          bigint       not null,
    code        varchar(50)  not null unique,
    name        varchar(255) not null,
    description text,
    target_date timestamp    not null,
    actual_date timestamp,
    status      varchar(50)  not null,
    product_id  bigint       not null,
    owner       varchar(255),
    notes       text,
    created_by  varchar(255) not null,
    created_at  timestamp    not null default current_timestamp,
    updated_by  varchar(255),
    updated_at  timestamp,
    primary key (id)
);

create sequence if not exists milestone_id_seq start with 100 increment by 50;
alter table releases add column if not exists milestone_id bigint;

delete from favorite_features;
delete from comments;
delete from features;
delete from releases;
delete from milestones;
delete from products;

insert into products (id, code, prefix, name, description, image_url, disabled, created_by, created_at) values
(1, 'intellij', 'IDEA', 'IntelliJ IDEA', 'JetBrains IDE for Java', 'https://resources.jetbrains.com/storage/products/company/brand/logos/IntelliJ_IDEA.png', false, 'admin', '2024-03-01 00:00:00'),
(2, 'goland','GO','GoLand', 'JetBrains IDE for Go', 'https://resources.jetbrains.com/storage/products/company/brand/logos/GoLand.png',false, 'admin','2024-03-01 00:00:00'),
(3, 'webstorm','WEB','WebStorm', 'JetBrains IDE for Web Development','https://resources.jetbrains.com/storage/products/company/brand/logos/WebStorm.png', false, 'admin','2024-03-01 00:00:00'),
(4, 'pycharm','PY','PyCharm', 'JetBrains IDE for Python', 'https://resources.jetbrains.com/storage/products/company/brand/logos/PyCharm.png',false, 'admin','2024-03-01 00:00:00'),
(5, 'rider', 'RIDER', 'Rider', 'JetBrains IDE for .NET', 'https://resources.jetbrains.com/storage/products/company/brand/logos/Rider.png', false, 'admin', '2024-03-01 00:00:00');

insert into milestones (id, code, name, description, target_date, actual_date, status, product_id, owner, notes, created_by, created_at, updated_by, updated_at) values
(1001, 'Q1-2024', 'Q1 2024 Release', 'First quarter objectives', '2024-03-31 23:59:59', '2024-03-28 15:30:00', 'COMPLETED', 1, 'alice@example.com', 'Completed ahead of schedule', 'admin', '2024-01-01 00:00:00', 'admin', '2024-03-28 15:30:00'),
(1002, 'Q2-2024', 'Q2 2024 Release', 'Second quarter objectives', '2024-06-30 23:59:59', NULL, 'IN_PROGRESS', 1, 'bob@example.com', 'Focus on performance', 'admin', '2024-04-01 00:00:00', NULL, NULL),
(1003, 'Q3-2024', 'Q3 2024 Release', 'Third quarter objectives', '2024-09-30 23:59:59', NULL, 'PLANNED', 2, 'charlie@example.com', 'New features planned', 'admin', '2024-07-01 00:00:00', NULL, NULL),
(1004, 'MVP-1', 'MVP Release', 'Minimum Viable Product', '2024-05-15 23:59:59', NULL, 'PLANNED', 3, 'dave@example.com', 'Core features only', 'admin', '2024-01-15 00:00:00', NULL, NULL),
(1005, 'PERF-2024', 'Performance Release', 'Performance improvements', '2024-08-31 23:59:59', NULL, 'PLANNED', 5, 'eve@example.com', 'Performance focus', 'admin', '2024-02-01 00:00:00', NULL, NULL);

insert into releases (id, product_id, milestone_id, code, description, status, created_by, created_at) values
(1, 1, 1001, 'IDEA-2023.3.8', 'IntelliJ IDEA 2023.3.8', 'RELEASED', 'admin','2023-03-25'),
(2, 1, 1002, 'IDEA-2024.2.3', 'IntelliJ IDEA 2024.2.4', 'RELEASED', 'admin','2024-02-25'),
(3, 2, 1003, 'GO-2024.2.3', 'GoLand 2024.2.4', 'RELEASED', 'admin','2024-02-15'),
(4, 3, 1004, 'WEB-2024.2.3', 'WebStorm 2024.2.4', 'RELEASED', 'admin','2024-02-20'),
(5, 4, NULL, 'PY-2024.2.3', 'PyCharm 2024.2.4', 'RELEASED', 'admin','2024-02-20'),
(6, 5, 1005, 'RIDER-2024.2.6', 'Rider 2024.2.6', 'RELEASED', 'admin','2024-02-16')
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