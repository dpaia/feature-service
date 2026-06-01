-- Add new columns for advanced release query and planning management
ALTER TABLE releases ADD COLUMN planned_release_date TIMESTAMP;
ALTER TABLE releases ADD COLUMN release_owner VARCHAR(255);
ALTER TABLE releases ADD COLUMN release_version INTEGER NOT NULL DEFAULT 1;

-- Create indexes for filtering and query performance
CREATE INDEX idx_releases_status ON releases(status);
CREATE INDEX idx_releases_planned_release_date ON releases(planned_release_date);
CREATE INDEX idx_releases_release_owner ON releases(release_owner);

-- Add comment for documentation
COMMENT ON COLUMN releases.planned_release_date IS 'Target date when the release is planned to be delivered';
COMMENT ON COLUMN releases.release_owner IS 'User responsible for the release (can be different from creator)';
COMMENT ON COLUMN releases.release_version IS 'Version number for optimistic locking on status changes';
