-- Add priority field to features table
ALTER TABLE features ADD COLUMN priority VARCHAR(50) DEFAULT 'MEDIUM';

-- Add index for querying features by priority
CREATE INDEX idx_features_priority ON features(priority);