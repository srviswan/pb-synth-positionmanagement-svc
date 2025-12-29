-- Initial schema for SQL Server
-- Event Store table
CREATE TABLE event_store (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    position_key NVARCHAR(255) NOT NULL,
    event_ver INT NOT NULL,
    event_type NVARCHAR(50) NOT NULL,
    event_data NVARCHAR(MAX) NOT NULL, -- JSON stored as NVARCHAR(MAX)
    effective_date DATE NOT NULL,
    occurred_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    correlation_id NVARCHAR(255),
    causation_id NVARCHAR(255),
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT uq_event_store_position_version UNIQUE (position_key, event_ver)
);

CREATE INDEX idx_event_store_position_key ON event_store(position_key);
CREATE INDEX idx_event_store_effective_date ON event_store(effective_date);
CREATE INDEX idx_event_store_occurred_at ON event_store(occurred_at);

-- Snapshot table
CREATE TABLE snapshot (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    position_key NVARCHAR(255) NOT NULL UNIQUE,
    snapshot_data NVARCHAR(MAX) NOT NULL, -- JSON stored as NVARCHAR(MAX)
    version INT NOT NULL,
    effective_date DATE NOT NULL,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

CREATE INDEX idx_snapshot_position_key ON snapshot(position_key);
CREATE INDEX idx_snapshot_effective_date ON snapshot(effective_date);

-- Idempotency table
CREATE TABLE idempotency (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    message_id NVARCHAR(255) NOT NULL UNIQUE,
    position_key NVARCHAR(255) NOT NULL,
    processed_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

CREATE INDEX idx_idempotency_message_id ON idempotency(message_id);
CREATE INDEX idx_idempotency_position_key ON idempotency(position_key);
