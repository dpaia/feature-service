-- This table stores all feature-related events with full metadata and payload for replay support

CREATE TABLE event_store (
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN ('API', 'EVENT')),
    operation_type VARCHAR(50) NOT NULL CHECK (operation_type IN ('CREATED', 'UPDATED', 'DELETED')),
    feature_id BIGINT,
    feature_code VARCHAR(255),
    event_payload TEXT NOT NULL,
    event_timestamp TIMESTAMP NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    result_data TEXT,
    replay_count INTEGER NOT NULL DEFAULT 0,
    last_replayed_at TIMESTAMP NULL,
    replay_status VARCHAR(20) NULL CHECK (replay_status IN ('SUCCESS', 'FAILED')),
    PRIMARY KEY (event_id, event_type)
);

-- Create indexes for efficient querying during replay
CREATE INDEX idx_event_store_timestamp ON event_store(event_timestamp);
CREATE INDEX idx_event_store_feature_code ON event_store(feature_code);
CREATE INDEX idx_event_store_feature_id ON event_store(feature_id);
CREATE INDEX idx_event_store_operation_type ON event_store(operation_type);
CREATE INDEX idx_event_store_event_type ON event_store(event_type);
CREATE INDEX idx_event_store_expires_at ON event_store(expires_at);

-- Create composite index for replay queries (time range + feature filtering)
CREATE INDEX idx_event_store_replay ON event_store(event_timestamp, feature_code, operation_type);

-- Create index for replay tracking
CREATE INDEX idx_event_store_replay_tracking ON event_store(replay_count, last_replayed_at);

-- Add comments for documentation
COMMENT ON TABLE event_store IS 'Event store for feature events with deduplication and replay support. Stores all feature-related events with full metadata and payload for idempotent replay.';
COMMENT ON COLUMN event_store.event_id IS 'Unique event identifier (UUID)';
COMMENT ON COLUMN event_store.event_type IS 'Event type: API for API-level idempotency, EVENT for Kafka event-level deduplication';
COMMENT ON COLUMN event_store.operation_type IS 'Type of operation: CREATED, UPDATED, DELETED';
COMMENT ON COLUMN event_store.feature_id IS 'Database ID of the feature (nullable for deleted features)';
COMMENT ON COLUMN event_store.feature_code IS 'Business code of the feature (e.g., PROD-123)';
COMMENT ON COLUMN event_store.event_payload IS 'Full JSON payload of the event for replay';
COMMENT ON COLUMN event_store.event_timestamp IS 'Timestamp when the event occurred (business timestamp)';
COMMENT ON COLUMN event_store.processed_at IS 'When the event was first processed';
COMMENT ON COLUMN event_store.expires_at IS 'When this record should be cleaned up (TTL)';
COMMENT ON COLUMN event_store.result_data IS 'Stores the result of the processed operation for true idempotency (e.g., feature code for create operations)';
COMMENT ON COLUMN event_store.replay_count IS 'Number of times this event has been replayed (0 = never replayed)';
COMMENT ON COLUMN event_store.last_replayed_at IS 'Timestamp when this event was last replayed (NULL = never replayed)';
COMMENT ON COLUMN event_store.replay_status IS 'Status of last replay attempt: NULL (never replayed), SUCCESS, or FAILED';