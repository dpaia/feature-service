-- Create processed_events table for event deduplication
-- Uses composite primary key (event_id, event_type) for dual-level deduplication
-- Supports both API-level idempotency and Event-level deduplication

CREATE TABLE processed_events (
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN ('API', 'EVENT')),
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    result_data TEXT,
    PRIMARY KEY (event_id, event_type)
);

-- Create index for TTL cleanup queries
CREATE INDEX idx_processed_events_expires_at ON processed_events(expires_at);

-- Add comments for documentation
COMMENT ON TABLE processed_events IS 'Stores processed event IDs for deduplication with composite key (event_id, event_type) and operation results for true idempotency';
COMMENT ON COLUMN processed_events.event_id IS 'Unique event identifier (UUID)';
COMMENT ON COLUMN processed_events.event_type IS 'Event type: API for API-level idempotency, EVENT for Kafka event-level deduplication';
COMMENT ON COLUMN processed_events.processed_at IS 'When the event was first processed';
COMMENT ON COLUMN processed_events.expires_at IS 'When this record should be cleaned up (TTL)';
COMMENT ON COLUMN processed_events.result_data IS 'Stores the result of the processed operation for true idempotency (e.g., feature code for create operations)';