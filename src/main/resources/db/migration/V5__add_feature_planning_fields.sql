-- Add planning fields to features table
ALTER TABLE features ADD COLUMN planned_completion_date DATE;
ALTER TABLE features ADD COLUMN actual_completion_date DATE;
ALTER TABLE features ADD COLUMN feature_planning_status VARCHAR(50);
ALTER TABLE features ADD COLUMN feature_owner VARCHAR(255);
ALTER TABLE features ADD COLUMN blockage_reason TEXT;

-- Create index on feature_planning_status for better query performance
CREATE INDEX idx_features_planning_status ON features(feature_planning_status);

-- Create index on feature_owner for better query performance
CREATE INDEX idx_features_owner ON features(feature_owner);

-- Create index on planned_completion_date for date range queries
CREATE INDEX idx_features_planned_completion_date ON features(planned_completion_date);