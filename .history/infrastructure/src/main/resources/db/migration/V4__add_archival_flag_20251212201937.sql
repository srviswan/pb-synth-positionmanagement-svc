-- Migration: Add Archival Flag to All Tables
-- Enables partition-level archival by marking records for archival
-- Supports moving entire partitions to archival storage

-- ========== EVENT STORE ==========
ALTER TABLE event_store 
ADD COLUMN archival_flag BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE event_store 
ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX idx_event_store_archival_flag ON event_store (archival_flag) 
WHERE archival_flag = FALSE;

CREATE INDEX idx_event_store_archived_at ON event_store (archived_at) 
WHERE archived_at IS NOT NULL;

CREATE INDEX idx_event_store_archival_partition ON event_store (archival_flag, position_key, effective_date)
WHERE archival_flag = TRUE;

COMMENT ON COLUMN event_store.archival_flag IS 'Flag indicating if event is marked for archival. Used for partition-level archival operations.';
COMMENT ON COLUMN event_store.archived_at IS 'Timestamp when the event/partition was archived. NULL for active data.';

-- ========== SNAPSHOT STORE ==========
ALTER TABLE snapshot_store 
ADD COLUMN archival_flag BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE snapshot_store 
ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX idx_snapshot_store_archival_flag ON snapshot_store (archival_flag) 
WHERE archival_flag = FALSE;

CREATE INDEX idx_snapshot_store_archived_at ON snapshot_store (archived_at) 
WHERE archived_at IS NOT NULL;

COMMENT ON COLUMN snapshot_store.archival_flag IS 'Flag indicating if snapshot is marked for archival. Used for partition-level archival operations.';
COMMENT ON COLUMN snapshot_store.archived_at IS 'Timestamp when the snapshot was archived. NULL for active data.';

-- ========== IDEMPOTENCY STORE ==========
ALTER TABLE idempotency_store 
ADD COLUMN archival_flag BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE idempotency_store 
ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX idx_idempotency_store_archival_flag ON idempotency_store (archival_flag) 
WHERE archival_flag = FALSE;

CREATE INDEX idx_idempotency_store_archived_at ON idempotency_store (archived_at) 
WHERE archived_at IS NOT NULL;

COMMENT ON COLUMN idempotency_store.archival_flag IS 'Flag indicating if idempotency record is marked for archival.';
COMMENT ON COLUMN idempotency_store.archived_at IS 'Timestamp when the idempotency record was archived. NULL for active data.';

-- ========== RECONCILIATION BREAKS ==========
ALTER TABLE reconciliation_breaks 
ADD COLUMN archival_flag BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE reconciliation_breaks 
ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX idx_reconciliation_breaks_archival_flag ON reconciliation_breaks (archival_flag) 
WHERE archival_flag = FALSE;

CREATE INDEX idx_reconciliation_breaks_archived_at ON reconciliation_breaks (archived_at) 
WHERE archived_at IS NOT NULL;

COMMENT ON COLUMN reconciliation_breaks.archival_flag IS 'Flag indicating if reconciliation break is marked for archival.';
COMMENT ON COLUMN reconciliation_breaks.archived_at IS 'Timestamp when the reconciliation break was archived. NULL for active data.';

-- ========== REGULATORY SUBMISSIONS ==========
ALTER TABLE regulatory_submissions 
ADD COLUMN archival_flag BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE regulatory_submissions 
ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX idx_regulatory_submissions_archival_flag ON regulatory_submissions (archival_flag) 
WHERE archival_flag = FALSE;

CREATE INDEX idx_regulatory_submissions_archived_at ON regulatory_submissions (archived_at) 
WHERE archived_at IS NOT NULL;

COMMENT ON COLUMN regulatory_submissions.archival_flag IS 'Flag indicating if regulatory submission is marked for archival.';
COMMENT ON COLUMN regulatory_submissions.archived_at IS 'Timestamp when the regulatory submission was archived. NULL for active data.';

-- ========== UPI HISTORY ==========
ALTER TABLE upi_history 
ADD COLUMN archival_flag BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE upi_history 
ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX idx_upi_history_archival_flag ON upi_history (archival_flag) 
WHERE archival_flag = FALSE;

CREATE INDEX idx_upi_history_archived_at ON upi_history (archived_at) 
WHERE archived_at IS NOT NULL;

COMMENT ON COLUMN upi_history.archival_flag IS 'Flag indicating if UPI history record is marked for archival.';
COMMENT ON COLUMN upi_history.archived_at IS 'Timestamp when the UPI history record was archived. NULL for active data.';
