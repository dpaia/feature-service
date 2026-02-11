CREATE TABLE error_log (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    error_type VARCHAR(50) NOT NULL,
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    event_payload TEXT,
    user_id VARCHAR(255),
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Single index for date range queries
CREATE INDEX idx_error_log_timestamp ON error_log(timestamp);
CREATE INDEX idx_error_log_error_type ON error_log(error_type);
