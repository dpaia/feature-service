-- Add event_hash column for deduplication during reprocessing
-- This will prevent duplicate feature usage events when reprocessing errors

ALTER TABLE feature_usage 
ADD COLUMN event_hash VARCHAR(64);

-- Create unique constraint on event_hash to prevent duplicates
ALTER TABLE feature_usage 
ADD CONSTRAINT uk_feature_usage_event_hash UNIQUE (event_hash);

-- Create index for efficient hash lookups
CREATE INDEX idx_feature_usage_event_hash ON feature_usage (event_hash);