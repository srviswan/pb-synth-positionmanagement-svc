-- Add lookup fields to snapshot_store for efficient querying
-- These fields are denormalized from the event payload for better query performance

ALTER TABLE snapshot_store
    ADD COLUMN IF NOT EXISTS account VARCHAR(128),
    ADD COLUMN IF NOT EXISTS instrument VARCHAR(64),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10),
    ADD COLUMN IF NOT EXISTS contract_id VARCHAR(64);

-- Create indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_snapshot_account ON snapshot_store (account) WHERE account IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_snapshot_instrument ON snapshot_store (instrument) WHERE instrument IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_snapshot_currency ON snapshot_store (currency) WHERE currency IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_snapshot_contract_id ON snapshot_store (contract_id) WHERE contract_id IS NOT NULL;

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_snapshot_account_instrument ON snapshot_store (account, instrument) 
    WHERE account IS NOT NULL AND instrument IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_snapshot_account_instrument_currency ON snapshot_store (account, instrument, currency) 
    WHERE account IS NOT NULL AND instrument IS NOT NULL AND currency IS NOT NULL;

-- Index for filtering by status with lookup fields
CREATE INDEX IF NOT EXISTS idx_snapshot_account_status ON snapshot_store (account, status) 
    WHERE account IS NOT NULL AND archival_flag = false;
