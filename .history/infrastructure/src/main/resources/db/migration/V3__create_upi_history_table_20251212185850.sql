-- Migration: Create UPI History Table
-- Tracks all UPI changes for audit purposes

CREATE TABLE IF NOT EXISTS upi_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    position_key VARCHAR(64) NOT NULL,
    upi VARCHAR(128) NOT NULL,
    previous_upi VARCHAR(128),
    status VARCHAR(20) NOT NULL,
    previous_status VARCHAR(20),
    change_type VARCHAR(50) NOT NULL,
    triggering_trade_id VARCHAR(128),
    backdated_trade_id VARCHAR(128),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_date DATE NOT NULL,
    reason VARCHAR(255),
    merged_from_position_key VARCHAR(64),
    CONSTRAINT fk_upi_history_position FOREIGN KEY (position_key) REFERENCES snapshot_store(position_key)
);

-- Indexes for performance
CREATE INDEX idx_upi_history_position_key ON upi_history(position_key);
CREATE INDEX idx_upi_history_upi ON upi_history(upi);
CREATE INDEX idx_upi_history_occurred_at ON upi_history(occurred_at);
CREATE INDEX idx_upi_history_change_type ON upi_history(change_type);
CREATE INDEX idx_upi_history_effective_date ON upi_history(effective_date);
CREATE INDEX idx_upi_history_merged_from ON upi_history(merged_from_position_key) WHERE merged_from_position_key IS NOT NULL;

COMMENT ON TABLE upi_history IS 'Tracks all UPI (Unique Position Identifier) changes for audit and compliance purposes';
COMMENT ON COLUMN upi_history.change_type IS 'Type of change: CREATED, TERMINATED, REOPENED, INVALIDATED, MERGED, RESTORED';
COMMENT ON COLUMN upi_history.merged_from_position_key IS 'If change_type is MERGED, this indicates the source position that was merged into this position';
COMMENT ON COLUMN upi_history.backdated_trade_id IS 'If change was caused by a backdated trade, this tracks the trade ID';
