-- V6: Add advanced analytics indexes for performance optimization
-- Task 3.1: Foundation + Usage Trends Analytics
-- Note: Some basic indexes already exist in V5, only adding new composite indexes

-- Composite index for trend analysis queries with feature filtering
-- Optimizes DATE_TRUNC queries filtered by feature_code
CREATE INDEX idx_feature_usage_timestamp_feature
ON feature_usage(timestamp DESC, feature_code)
WHERE feature_code IS NOT NULL;

-- Composite index for trend analysis queries with product filtering
-- Optimizes DATE_TRUNC queries filtered by product_code
CREATE INDEX idx_feature_usage_timestamp_product
ON feature_usage(timestamp DESC, product_code)
WHERE product_code IS NOT NULL;

-- Index for user-based analytics with time filtering
-- Optimizes user-specific analytics with time range
CREATE INDEX idx_feature_usage_user_timestamp
ON feature_usage(user_id, timestamp DESC);

-- Composite index for complex multi-dimensional queries
-- Supports filtering by multiple criteria simultaneously
CREATE INDEX idx_feature_usage_composite
ON feature_usage(timestamp, feature_code, product_code, action_type)
WHERE feature_code IS NOT NULL AND product_code IS NOT NULL;

-- Index for release-specific queries (for future Task 3.2)
-- Optimizes release-specific analytics
CREATE INDEX idx_feature_usage_release_timestamp
ON feature_usage(release_code, timestamp DESC)
WHERE release_code IS NOT NULL;

-- Note: idx_feature_usage_timestamp_action already exists in V5
-- Note: Basic single-column indexes already exist in V5:
--   - idx_feature_usage_user_id
--   - idx_feature_usage_feature_code
--   - idx_feature_usage_product_code
--   - idx_feature_usage_release_code
--   - idx_feature_usage_action_type
--   - idx_feature_usage_timestamp
--   - idx_feature_usage_timestamp_action