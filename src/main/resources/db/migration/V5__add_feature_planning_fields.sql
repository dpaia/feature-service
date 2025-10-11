-- Add feature planning fields to features table
ALTER TABLE features
ADD COLUMN planned_completion_date DATE,
ADD COLUMN actual_completion_date DATE,
ADD COLUMN feature_planning_status VARCHAR(50),
ADD COLUMN feature_owner VARCHAR(255),
ADD COLUMN blockage_reason TEXT;

-- Create indexes for better query performance
CREATE INDEX idx_features_planning_status ON features(feature_planning_status);

CREATE INDEX idx_features_owner ON features(feature_owner);

CREATE INDEX idx_features_planned_completion ON features(planned_completion_date);
