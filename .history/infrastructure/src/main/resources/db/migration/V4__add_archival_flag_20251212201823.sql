-- Migration: Add Archival Flag to Event Store
-- Enables partition-level archival by marking events for archival
-- Supports moving entire partitions to archival storage

-- Add archival flag column (defaults to false for active data)
ALTER TABLE event_store 
ADD COLUMN archival_flag BOOLEAN NOT NULL DEFAULT FALSE;

-- Add archived_at timestamp (when the event/partition was archived)
ALTER TABLE event_store 
ADD COLUMN archived_at TIMESTAMPTZ;

-- Add index on archival_flag for efficient filtering of active vs archived data
CREATE INDEX idx_event_store_archival_flag ON event_store (archival_flag) 
WHERE archival_flag = FALSE; -- Partial index for active data only

-- Add index on archived_at for archival queries
CREATE INDEX idx_event_store_archived_at ON event_store (archived_at) 
WHERE archived_at IS NOT NULL;

-- Add composite index for archival queries by partition
CREATE INDEX idx_event_store_archival_partition ON event_store (archival_flag, position_key, effective_date)
WHERE archival_flag = TRUE;

-- Comments for documentation
COMMENT ON COLUMN event_store.archival_flag IS 'Flag indicating if event is marked for archival. Used for partition-level archival operations.';
COMMENT ON COLUMN event_store.archived_at IS 'Timestamp when the event/partition was archived. NULL for active data.';
