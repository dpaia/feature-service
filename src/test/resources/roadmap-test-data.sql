-- Additional data for roadmap tests

-- Set owner and dates on existing releases
UPDATE releases SET
    owner = 'john.doe',
    planned_release_date = '2023-12-15 00:00:00',
    actual_release_date = '2023-12-10 00:00:00'
WHERE code = 'IDEA-2023.3.8';

UPDATE releases SET
    owner = 'john.doe',
    planned_release_date = '2024-01-01 00:00:00',
    actual_release_date = '2024-01-20 00:00:00'
WHERE code = 'IDEA-2024.2.3';

-- Add features with various statuses to IDEA-2023.3.8 (release id=1)
INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, created_at) VALUES
(100, 1, 1, 'IDEA-100', 'Released feature', 'Released feature', 'RELEASED', 'admin', '2024-01-01 00:00:00'),
(101, 1, 1, 'IDEA-101', 'On hold feature', 'On hold feature', 'ON_HOLD', 'admin', '2024-01-01 00:00:00');

-- Future release (ON_SCHEDULE)
INSERT INTO releases (id, product_id, code, description, status, owner, planned_release_date, created_by, created_at) VALUES
(100, 1, 'IDEA-FUTURE', 'Future release', 'PLANNED', 'jane.doe', '2099-12-31 00:00:00', 'admin', '2025-01-01 00:00:00');

-- Overdue release without actual date (CRITICAL - 14+ days past planned)
INSERT INTO releases (id, product_id, code, description, status, owner, planned_release_date, created_by, created_at) VALUES
(101, 2, 'GO-CRITICAL', 'Critical release', 'IN_PROGRESS', 'john.doe', '2020-01-01 00:00:00', 'admin', '2020-01-01 00:00:00');

-- Release with no planned date (no timeline adherence)
INSERT INTO releases (id, product_id, code, description, status, owner, created_by, created_at) VALUES
(102, 3, 'WEB-DRAFT', 'Draft release', 'DRAFT', 'jane.doe', 'admin', '2025-01-01 00:00:00');
