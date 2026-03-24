-- Insert sample milestone data
insert into milestones (id, code, name, description, target_date, actual_date, status, product_id, owner, notes, created_by, created_at) values
(1, 'Q1-2025', 'Q1 2025 Release', 'First quarter objectives and deliverables', '2025-03-31 23:59:59', null, 'IN_PROGRESS', 1, 'alice@example.com', 'Focus on performance improvements', 'admin', '2025-01-01 00:00:00'),
(2, 'Q2-2025', 'Q2 2025 Release', 'Second quarter objectives and deliverables', '2025-06-30 23:59:59', null, 'PLANNED', 1, 'bob@example.com', 'Focus on new features', 'admin', '2025-01-01 00:00:00'),
(3, 'MVP-1', 'MVP Release 1', 'Minimum viable product first release', '2025-05-15 23:59:59', null, 'IN_PROGRESS', 2, 'charlie@example.com', 'Core functionality delivery', 'admin', '2025-01-01 00:00:00'),
(4, 'BETA-1', 'Beta Release 1', 'First beta release for testing', '2025-04-30 23:59:59', null, 'PLANNED', 3, 'diana@example.com', 'Beta testing phase', 'admin', '2025-01-01 00:00:00'),
(5, 'STABLE-1', 'Stable Release 1', 'First stable production release', '2025-07-31 23:59:59', null, 'PLANNED', 4, 'eve@example.com', 'Production ready release', 'admin', '2025-01-01 00:00:00');

-- Update some releases to associate them with milestones
update releases set milestone_id = 1 where id = 1; -- IDEA-2025.1 -> Q1-2025
update releases set milestone_id = 1 where id = 2; -- IDEA-2025.2 -> Q1-2025
update releases set milestone_id = 3 where id = 3; -- GO-2025.1 -> MVP-1
update releases set milestone_id = 4 where id = 4; -- WEB-2025.1 -> BETA-1