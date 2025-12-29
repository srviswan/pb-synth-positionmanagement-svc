-- Add account and instrument fields to snapshot for querying
ALTER TABLE snapshot ADD account NVARCHAR(255) NULL;
ALTER TABLE snapshot ADD instrument NVARCHAR(255) NULL;

-- Add indexes for account and instrument queries
CREATE INDEX idx_snapshot_account ON snapshot(account);
CREATE INDEX idx_snapshot_instrument ON snapshot(instrument);
CREATE INDEX idx_snapshot_account_instrument ON snapshot(account, instrument);
