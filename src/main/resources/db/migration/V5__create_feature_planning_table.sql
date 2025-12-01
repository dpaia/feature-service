-- Add feature planning fields to existing features table
ALTER TABLE features ADD COLUMN planned_completion_date DATE;
ALTER TABLE features ADD COLUMN planning_status VARCHAR(50);
ALTER TABLE features ADD COLUMN feature_owner VARCHAR(255);
ALTER TABLE features ADD COLUMN notes TEXT;
ALTER TABLE features ADD COLUMN blockage_reason TEXT;

-- Add index for querying features by planning status
CREATE INDEX idx_features_planning_status ON features(planning_status);

-- Add index for querying features by feature owner
CREATE INDEX idx_features_feature_owner ON features(feature_owner);

-- Add index for querying overdue features (planned completion date in the past)
CREATE INDEX idx_features_planned_completion ON features(planned_completion_date);