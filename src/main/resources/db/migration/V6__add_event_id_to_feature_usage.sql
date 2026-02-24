ALTER TABLE feature_usage ADD COLUMN event_id varchar(36);

CREATE UNIQUE INDEX idx_feature_usage_event_id ON feature_usage (event_id) WHERE event_id IS NOT NULL;
