-- Position Management Service - Initial Schema for MS SQL Server
-- Event-Sourced Position Service with Hotpath/Coldpath Architecture

-- Note: MS SQL Server doesn't support hash partitioning like PostgreSQL
-- We'll use a computed column for partition key and create filegroups/partition function if needed
-- For now, we'll create a non-partitioned table with indexes for performance

-- 2. EVENT STORE (Non-partitioned in SQL Server, but indexed for performance)
-- Strategy: Append-Only Log. No Updates allowed.
CREATE TABLE event_store (
    position_key    VARCHAR(64) NOT NULL, -- Hash(Acct+Instr+Ccy)
    event_ver       BIGINT NOT NULL,      -- Strictly increasing sequence
    event_type      VARCHAR(30) NOT NULL, -- NEW_TRADE, RESET, DECREASE, CORRECTION, etc.
    effective_date  DATE NOT NULL,        -- Business Date (Valid Time)
    occurred_at     DATETIMEOFFSET DEFAULT SYSDATETIMEOFFSET(), -- System Date (Transaction Time)
    payload         NVARCHAR(MAX) NOT NULL,       -- CDM-inspired Event Details (JSON)
    meta_lots       NVARCHAR(MAX),                -- Audit: Lot allocation map for this event (JSON)
    correlation_id  VARCHAR(128),         -- Trace ID from Upstream
    causation_id    VARCHAR(128),         -- Parent event ID
    contract_id     VARCHAR(64),          -- Link to Contract Service rules
    user_id         VARCHAR(64),          -- Who initiated the action
    archival_flag   BIT NOT NULL DEFAULT 0,
    archived_at     DATETIMEOFFSET,
    
    PRIMARY KEY (position_key, event_ver),
    CONSTRAINT chk_event_type CHECK (event_type IN ('NEW_TRADE','INCREASE','DECREASE','RESET','CORRECTION','POSITION_CLOSED','PROVISIONAL_TRADE_APPLIED','HISTORICAL_POSITION_CORRECTED'))
);

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
    tax_lots_compressed NVARCHAR(MAX) NOT NULL, 
    
    summary_metrics     NVARCHAR(MAX), -- { "net_qty": 1000, "exposure": 50000 }
    price_quantity_schedule NVARCHAR(MAX), -- CDM-inspired PriceQuantity schedule
    last_updated_at     DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    version             BIGINT NOT NULL DEFAULT 0, -- For optimistic locking
    archival_flag       BIT NOT NULL DEFAULT 0,
    archived_at         DATETIMEOFFSET,
    
    -- Lookup fields for efficient querying
    account             VARCHAR(128),
    instrument          VARCHAR(64),
    currency            VARCHAR(10),
    contract_id         VARCHAR(64),
    
    CONSTRAINT chk_snapshot_status CHECK (status IN ('ACTIVE','TERMINATED')),
    CONSTRAINT chk_reconciliation_status CHECK (reconciliation_status IN ('RECONCILED','PROVISIONAL','PENDING'))
);

-- Indexes for Snapshot Store
CREATE INDEX idx_snapshot_reconciliation_status ON snapshot_store (reconciliation_status);
CREATE INDEX idx_snapshot_last_updated ON snapshot_store (last_updated_at);
CREATE INDEX idx_snapshot_status ON snapshot_store (status);
CREATE INDEX idx_snapshot_account ON snapshot_store (account);
CREATE INDEX idx_snapshot_instrument ON snapshot_store (instrument);
CREATE INDEX idx_snapshot_currency ON snapshot_store (currency);
CREATE INDEX idx_snapshot_contract_id ON snapshot_store (contract_id);
CREATE INDEX idx_snapshot_account_instrument ON snapshot_store (account, instrument);
CREATE INDEX idx_snapshot_account_instrument_currency ON snapshot_store (account, instrument, currency);
CREATE INDEX idx_snapshot_account_status ON snapshot_store (account, status);

-- 4. IDEMPOTENCY STORE
-- Prevents duplicate processing of trades
CREATE TABLE idempotency_store (
    idempotency_key    VARCHAR(128) PRIMARY KEY,
    trade_id           VARCHAR(128) NOT NULL UNIQUE,
    position_key       VARCHAR(64) NOT NULL,
    event_version      BIGINT,
    processed_at       DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    status             VARCHAR(20) NOT NULL, -- PROCESSED, FAILED
    correlation_id     VARCHAR(128),
    archival_flag      BIT NOT NULL DEFAULT 0,
    archived_at        DATETIMEOFFSET
);

CREATE INDEX idx_idempotency_trade_id ON idempotency_store (trade_id);
CREATE INDEX idx_idempotency_processed_at ON idempotency_store (processed_at);

-- 5. RECONCILIATION BREAKS
-- Tracks discrepancies between internal and external positions
CREATE TABLE reconciliation_breaks (
    break_id           UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    position_key       VARCHAR(64) NOT NULL,
    break_type         VARCHAR(50) NOT NULL, -- QUANTITY_MISMATCH, PRICE_MISMATCH, etc.
    severity           VARCHAR(20) NOT NULL, -- CRITICAL, WARNING, INFO
    internal_value     NVARCHAR(MAX), -- JSON
    external_value     NVARCHAR(MAX), -- JSON
    regulatory_value   NVARCHAR(MAX), -- JSON
    detected_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    resolved_at        DATETIMEOFFSET,
    status             VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, INVESTIGATING, RESOLVED
    resolution_notes   NVARCHAR(MAX),
    assigned_to        VARCHAR(64),
    archival_flag      BIT NOT NULL DEFAULT 0,
    archived_at        DATETIMEOFFSET
);

CREATE INDEX idx_position_key ON reconciliation_breaks (position_key);
CREATE INDEX idx_status ON reconciliation_breaks (status);
CREATE INDEX idx_detected_at ON reconciliation_breaks (detected_at);

-- 6. REGULATORY SUBMISSIONS
-- Tracks all regulatory submissions
CREATE TABLE regulatory_submissions (
    submission_id      UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    trade_id           VARCHAR(128) NOT NULL,
    position_key       VARCHAR(64) NOT NULL,
    submission_type    VARCHAR(50) NOT NULL, -- TRADE_REPORT, POSITION_REPORT, etc.
    submitted_at       DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    status             VARCHAR(20) NOT NULL, -- PENDING, SUBMITTED, ACCEPTED, REJECTED
    response_received_at DATETIMEOFFSET,
    response_payload   NVARCHAR(MAX), -- JSON
    error_message      NVARCHAR(MAX),
    retry_count        INT NOT NULL DEFAULT 0,
    correlation_id     VARCHAR(128),
    archival_flag      BIT NOT NULL DEFAULT 0,
    archived_at        DATETIMEOFFSET
);

CREATE INDEX idx_regulatory_trade_id ON regulatory_submissions (trade_id);
CREATE INDEX idx_regulatory_status ON regulatory_submissions (status);
CREATE INDEX idx_regulatory_submitted_at ON regulatory_submissions (submitted_at);

-- 7. UPI HISTORY
-- Tracks all UPI changes for audit purposes
CREATE TABLE upi_history (
    history_id         UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    position_key       VARCHAR(64) NOT NULL,
    upi                VARCHAR(128) NOT NULL,
    previous_upi       VARCHAR(128),
    change_type        VARCHAR(50) NOT NULL, -- CREATED, TERMINATED, REOPENED, INVALIDATED, MERGED, RESTORED
    effective_date     DATE NOT NULL,
    occurred_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    triggering_trade_id VARCHAR(128),
    backdated_trade_id VARCHAR(128),
    previous_status    VARCHAR(20), -- ACTIVE, TERMINATED
    status             VARCHAR(20) NOT NULL, -- ACTIVE, TERMINATED
    merged_from_position_key VARCHAR(64),
    reason             VARCHAR(255),
    archival_flag      BIT NOT NULL DEFAULT 0,
    archived_at        DATETIMEOFFSET,
    
    CONSTRAINT chk_upi_history_status CHECK (status IN ('ACTIVE','TERMINATED')),
    CONSTRAINT chk_upi_history_previous_status CHECK (previous_status IS NULL OR previous_status IN ('ACTIVE','TERMINATED'))
);

CREATE INDEX idx_upi_history_position_key ON upi_history (position_key);
CREATE INDEX idx_upi_history_upi ON upi_history (upi);
CREATE INDEX idx_upi_history_occurred_at ON upi_history (occurred_at);
