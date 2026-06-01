-- Add planning fields to features table
ALTER TABLE features ADD COLUMN planned_completion_at timestamp;
ALTER TABLE features ADD COLUMN actual_completion_at timestamp;
ALTER TABLE features ADD COLUMN feature_planning_status VARCHAR(50);
ALTER TABLE features ADD COLUMN feature_owner VARCHAR(255);
ALTER TABLE features ADD COLUMN blockage_reason TEXT;

-- Create indexes for better query performance
CREATE INDEX idx_features_planning_status ON features(feature_planning_status);

CREATE INDEX idx_features_owner ON features(feature_owner);

CREATE INDEX idx_features_planned_completion_at ON features(planned_completion_at);
