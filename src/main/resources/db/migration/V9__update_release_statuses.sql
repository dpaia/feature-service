-- Update notifications table to support new release statuses
-- Add new release statuses to the event_type constraint

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_event_type_check;

ALTER TABLE notifications ADD CONSTRAINT notifications_event_type_check 
CHECK (event_type IN (
    'FEATURE_CREATED', 
    'FEATURE_UPDATED', 
    'FEATURE_DELETED', 
    'RELEASE_CREATED', 
    'RELEASE_UPDATED', 
    'RELEASE_DELETED'
));

-- Add comment about the new release statuses support
COMMENT ON CONSTRAINT notifications_event_type_check ON notifications IS 'Supports all feature and release event types including new release status transitions';