-- Create error_log table for tracking failures in usage event processing
CREATE TABLE error_log (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    error_type VARCHAR(50) NOT NULL,
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    event_payload TEXT,
    user_id VARCHAR(255),
    resolved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for efficient querying
CREATE INDEX idx_error_log_timestamp ON error_log(timestamp);
CREATE INDEX idx_error_log_error_type ON error_log(error_type);
CREATE INDEX idx_error_log_user_id ON error_log(user_id);
CREATE INDEX idx_error_log_resolved ON error_log(resolved);
