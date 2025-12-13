-- Position Management Service - Initial Schema
-- Event-Sourced Position Service with Hotpath/Coldpath Architecture

-- 1. ENABLE EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. EVENT STORE (Partitioned by Hash of Position Key)
-- Strategy: Append-Only Log. No Updates allowed.
CREATE TABLE event_store (
    position_key    VARCHAR(64) NOT NULL, -- Hash(Acct+Instr+Ccy)
    event_ver       BIGINT NOT NULL,      -- Strictly increasing sequence
    event_type      VARCHAR(30) NOT NULL, -- NEW_TRADE, RESET, DECREASE, CORRECTION, etc.
    effective_date  DATE NOT NULL,        -- Business Date (Valid Time)
    occurred_at     TIMESTAMPTZ DEFAULT NOW(), -- System Date (Transaction Time)
    payload         JSONB NOT NULL,       -- CDM-inspired Event Details
    meta_lots       JSONB,                -- Audit: Lot allocation map for this event
    correlation_id  VARCHAR(128),         -- Trace ID from Upstream
    causation_id    VARCHAR(128),         -- Parent event ID
    contract_id     VARCHAR(64),          -- Link to Contract Service rules
    user_id         VARCHAR(64),          -- Who initiated the action
    
    PRIMARY KEY (position_key, event_ver)
) PARTITION BY HASH (position_key);

-- Create 16 Partitions for high throughput
CREATE TABLE event_store_p0 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE event_store_p1 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE event_store_p2 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE event_store_p3 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE event_store_p4 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE event_store_p5 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE event_store_p6 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE event_store_p7 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE event_store_p8 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE event_store_p9 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE event_store_p10 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE event_store_p11 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE event_store_p12 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE event_store_p13 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE event_store_p14 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE event_store_p15 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 15);

-- Indexes for Replay Speed
CREATE INDEX idx_event_replay ON event_store (position_key, event_ver ASC);
CREATE INDEX idx_contract_link ON event_store (contract_id);
CREATE INDEX idx_correlation_id ON event_store (correlation_id);
CREATE INDEX idx_effective_date ON event_store (position_key, effective_date);

-- 3. SNAPSHOT STORE (The Hot Cache)
-- Strategy: Single Row per Position. Overwritten on every event.
CREATE TABLE snapshot_store (
    position_key        VARCHAR(64) PRIMARY KEY,
    last_ver            BIGINT NOT NULL,
    uti                 VARCHAR(128) NOT NULL, -- The Active USI/UTI
    status              VARCHAR(20) NOT NULL,  -- ACTIVE, TERMINATED
    reconciliation_status VARCHAR(20) NOT NULL DEFAULT 'RECONCILED', -- RECONCILED, PROVISIONAL, PENDING
    provisional_trade_id VARCHAR(128),         -- If PROVISIONAL, reference to backdated trade
    
    -- COMPRESSED JSON (Struct of Arrays Pattern)
    -- Format: { "ids": [...], "prices": [...], "dates": [...] }
    tax_lots_compressed JSONB NOT NULL, 
    
    summary_metrics     JSONB, -- { "net_qty": 1000, "exposure": 50000 }
    last_updated_at     TIMESTAMPTZ DEFAULT NOW(),
    version             BIGINT DEFAULT 0 -- For optimistic locking
);

-- Indexes for Snapshot Store
CREATE INDEX idx_snapshot_reconciliation_status ON snapshot_store (reconciliation_status);
CREATE INDEX idx_snapshot_last_updated ON snapshot_store (last_updated_at);
CREATE INDEX idx_snapshot_status ON snapshot_store (status);

-- 4. IDEMPOTENCY STORE
-- Prevents duplicate processing of trades
CREATE TABLE idempotency_store (
    idempotency_key    VARCHAR(128) PRIMARY KEY,
    trade_id           VARCHAR(128) NOT NULL UNIQUE,
    position_key       VARCHAR(64) NOT NULL,
    event_version      BIGINT,
    processed_at       TIMESTAMPTZ DEFAULT NOW(),
    status             VARCHAR(20) NOT NULL, -- PROCESSED, FAILED
    correlation_id     VARCHAR(128)
);

CREATE INDEX idx_idempotency_trade_id ON idempotency_store (trade_id);
CREATE INDEX idx_idempotency_processed_at ON idempotency_store (processed_at);

-- 5. RECONCILIATION BREAKS
-- Tracks discrepancies between internal and external positions
CREATE TABLE reconciliation_breaks (
    break_id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    position_key       VARCHAR(64) NOT NULL,
    break_type         VARCHAR(50) NOT NULL, -- QUANTITY_MISMATCH, PRICE_MISMATCH, etc.
    severity           VARCHAR(20) NOT NULL, -- CRITICAL, WARNING, INFO
    internal_value     JSONB,
    external_value     JSONB,
    regulatory_value   JSONB,
    detected_at        TIMESTAMPTZ DEFAULT NOW(),
    resolved_at        TIMESTAMPTZ,
    status             VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, INVESTIGATING, RESOLVED
    resolution_notes   TEXT,
    assigned_to        VARCHAR(64)
);

CREATE INDEX idx_position_key ON reconciliation_breaks (position_key);
CREATE INDEX idx_status ON reconciliation_breaks (status);
CREATE INDEX idx_detected_at ON reconciliation_breaks (detected_at);

-- 6. REGULATORY SUBMISSIONS
-- Tracks all regulatory submissions
CREATE TABLE regulatory_submissions (
    submission_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trade_id           VARCHAR(128) NOT NULL,
    position_key       VARCHAR(64) NOT NULL,
    submission_type    VARCHAR(50) NOT NULL, -- TRADE_REPORT, POSITION_REPORT, etc.
    submitted_at       TIMESTAMPTZ DEFAULT NOW(),
    status             VARCHAR(20) NOT NULL, -- PENDING, SUBMITTED, ACCEPTED, REJECTED
    response_received_at TIMESTAMPTZ,
    response_payload   JSONB,
    error_message      TEXT,
    retry_count        INT DEFAULT 0,
    correlation_id     VARCHAR(128)
);

CREATE INDEX idx_regulatory_trade_id ON regulatory_submissions (trade_id);
CREATE INDEX idx_regulatory_status ON regulatory_submissions (status);
CREATE INDEX idx_regulatory_submitted_at ON regulatory_submissions (submitted_at);
