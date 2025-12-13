-- FILE: src/main/resources/schema.sql

-- 1. ENABLE EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. EVENT STORE (Partitioned by Hash of Position Key)
-- Strategy: Append-Only Log. No Updates allowed.
CREATE TABLE event_store (
    position_key    VARCHAR(64) NOT NULL, -- Hash(Acct+Instr+Ccy)
    event_ver       BIGINT NOT NULL,      -- Strictly increasing sequence
    event_type      VARCHAR(30) NOT NULL, -- NEW_TRADE, RESET, DECREASE, CORRECTION
    effective_date  DATE NOT NULL,        -- Business Date (Valid Time)
    occurred_at     TIMESTAMPTZ DEFAULT NOW(), -- System Date (Transaction Time)
    payload         JSONB NOT NULL,       -- CDM-inspired Event Details
    meta_lots       JSONB,                -- Audit: Lot allocation map for this event
    correlation_id  VARCHAR(128),         -- Trace ID from Upstream
    contract_id     VARCHAR(64),          -- Link to Contract Service rules
    
    PRIMARY KEY (position_key, event_ver)
) PARTITION BY HASH (position_key);

-- Create 16 Partitions for high throughput
CREATE TABLE event_store_p0 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE event_store_p1 PARTITION OF event_store FOR VALUES WITH (MODULUS 16, REMAINDER 1);
-- ... (Repeat for p2 to p15) ...

-- Indexes for Replay Speed
CREATE INDEX idx_event_replay ON event_store (position_key, event_ver ASC);
CREATE INDEX idx_contract_link ON event_store (contract_id);

-- 3. SNAPSHOT STORE (The Hot Cache)
-- Strategy: Single Row per Position. Overwritten on every event.
CREATE TABLE snapshot_store (
    position_key        VARCHAR(64) PRIMARY KEY,
    last_ver            BIGINT NOT NULL,
    uti                 VARCHAR(128) NOT NULL, -- The Active USI/UTI
    status              VARCHAR(20) NOT NULL,  -- ACTIVE, TERMINATED
    
    -- COMPRESSED JSON (Struct of Arrays Pattern)
    -- Format: { "ids": [...], "prices": [...], "dates": [...] }
    tax_lots_compressed JSONB NOT NULL, 
    
    summary_metrics     JSONB, -- { "net_qty": 1000, "exposure": 50000 }
    last_updated_at     TIMESTAMPTZ DEFAULT NOW()
);