-- Enhanced schema for IMPLEMENTATION_PLAN.md requirements
-- Add reconciliation status, provisional tracking, contract_id

-- Add reconciliation_status and provisional fields to snapshot
ALTER TABLE snapshot ADD reconciliation_status NVARCHAR(20) NOT NULL DEFAULT 'RECONCILED';
ALTER TABLE snapshot ADD provisional_trade_id NVARCHAR(255) NULL;
ALTER TABLE snapshot ADD contract_id NVARCHAR(64) NULL;

-- Add contract_id to event_store
ALTER TABLE event_store ADD contract_id NVARCHAR(64) NULL;

-- Add indexes for reconciliation queries
CREATE INDEX idx_snapshot_reconciliation_status ON snapshot(reconciliation_status);
CREATE INDEX idx_snapshot_contract_id ON snapshot(contract_id);
CREATE INDEX idx_event_store_contract_id ON event_store(contract_id);

-- Enhance idempotency table with status
ALTER TABLE idempotency ADD status NVARCHAR(20) NOT NULL DEFAULT 'PROCESSED';
ALTER TABLE idempotency ADD event_version INT NULL;

-- Add index for idempotency queries
CREATE INDEX idx_idempotency_status ON idempotency(status);
