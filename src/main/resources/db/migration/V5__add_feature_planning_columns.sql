ALTER TABLE features
    ADD COLUMN planned_completion_date DATE,
    ADD COLUMN planning_status         VARCHAR(50),
    ADD COLUMN feature_owner           VARCHAR(255),
    ADD COLUMN notes                   TEXT,
    ADD COLUMN blockage_reason         TEXT;
