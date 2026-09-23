CREATE TABLE sm_position (
    position_key   NVARCHAR(64)   NOT NULL PRIMARY KEY,
    region         NVARCHAR(32)   NOT NULL,
    account        NVARCHAR(64)   NOT NULL,
    instrument     NVARCHAR(64)   NOT NULL,
    currency       NVARCHAR(8)    NOT NULL,
    status         NVARCHAR(16)   NOT NULL,
    upi            NVARCHAR(128)  NULL,
    total_qty      DECIMAL(28, 8) NOT NULL,
    avg_price      DECIMAL(28, 8) NOT NULL,
    open_lot_count INT            NOT NULL,
    realized_pnl   DECIMAL(28, 8) NOT NULL,
    as_of_date     DATE           NULL,
    next_lot_seq   BIGINT         NOT NULL,
    version        BIGINT         NOT NULL,
    updated_at     DATETIMEOFFSET NOT NULL
);

CREATE TABLE sm_trade (
    trade_id        NVARCHAR(128)  NOT NULL PRIMARY KEY,
    position_key    NVARCHAR(64)   NOT NULL,
    activity_type   NVARCHAR(16)   NOT NULL,
    quantity        DECIMAL(28, 8) NULL,
    price           DECIMAL(28, 8) NOT NULL,
    effective_date  DATE           NOT NULL,
    settlement_date DATE           NULL,
    region          NVARCHAR(32)   NOT NULL,
    applied_at      DATETIMEOFFSET NOT NULL,
    account         NVARCHAR(64)   NULL,
    instrument      NVARCHAR(64)   NULL,
    currency        NVARCHAR(8)    NULL
);

CREATE INDEX ix_sm_trade_position_date ON sm_trade (position_key, effective_date);
CREATE INDEX ix_sm_trade_region_date ON sm_trade (region, effective_date);

CREATE TABLE sm_lot (
    lot_id            NVARCHAR(64)   NOT NULL PRIMARY KEY,
    position_key      NVARCHAR(64)   NOT NULL,
    parent_lot_id     NVARCHAR(64)   NULL,
    opening_trade_id  NVARCHAR(128)  NOT NULL,
    closing_trade_id  NVARCHAR(128)  NULL,
    original_qty      DECIMAL(28, 8) NOT NULL,
    remaining_qty     DECIMAL(28, 8) NOT NULL,
    open_price        DECIMAL(28, 8) NOT NULL,
    close_price       DECIMAL(28, 8) NULL,
    realized_pnl      DECIMAL(28, 8) NOT NULL,
    status            NVARCHAR(16)   NOT NULL,
    effective_date    DATE           NOT NULL,
    settlement_date   DATE           NULL,
    closed_on         DATE           NULL
);

CREATE INDEX ix_sm_lot_position ON sm_lot (position_key, status);

CREATE TABLE sm_position_daily (
    position_key    NVARCHAR(64)   NOT NULL,
    business_date   DATE           NOT NULL,
    region          NVARCHAR(32)   NOT NULL,
    total_qty       DECIMAL(28, 8) NOT NULL,
    avg_price       DECIMAL(28, 8) NOT NULL,
    open_lot_count  INT            NOT NULL,
    status          NVARCHAR(16)   NOT NULL,
    upi             NVARCHAR(128)  NULL,
    realized_pnl    DECIMAL(28, 8) NOT NULL,
    CONSTRAINT pk_sm_position_daily PRIMARY KEY (position_key, business_date)
);

CREATE INDEX ix_sm_position_daily_region ON sm_position_daily (region, business_date);

CREATE TABLE sm_lot_daily (
    position_key    NVARCHAR(64)   NOT NULL,
    lot_id          NVARCHAR(64)   NOT NULL,
    business_date   DATE           NOT NULL,
    region          NVARCHAR(32)   NOT NULL,
    remaining_qty   DECIMAL(28, 8) NOT NULL,
    tradeable_qty   DECIMAL(28, 8) NOT NULL,
    original_qty    DECIMAL(28, 8) NOT NULL,
    open_price      DECIMAL(28, 8) NOT NULL,
    status          NVARCHAR(16)   NOT NULL,
    settlement_date DATE           NULL,
    realized_pnl    DECIMAL(28, 8) NOT NULL,
    CONSTRAINT pk_sm_lot_daily PRIMARY KEY (position_key, lot_id, business_date)
);

CREATE INDEX ix_sm_lot_daily_region ON sm_lot_daily (region, business_date, status);

CREATE TABLE sm_position_history (
    history_id       NVARCHAR(36)   NOT NULL PRIMARY KEY,
    position_key     NVARCHAR(64)   NOT NULL,
    trade_id         NVARCHAR(128)  NOT NULL,
    effective_date   DATE           NOT NULL,
    qty_before       DECIMAL(28, 8) NOT NULL,
    qty_after        DECIMAL(28, 8) NOT NULL,
    status_before    NVARCHAR(16)   NULL,
    status_after     NVARCHAR(16)   NOT NULL,
    open_price_after DECIMAL(28, 8) NOT NULL
);

CREATE INDEX ix_sm_position_history_position ON sm_position_history (position_key, effective_date);

CREATE TABLE sm_upi_history (
    history_id     NVARCHAR(36)   NOT NULL PRIMARY KEY,
    position_key   NVARCHAR(64)   NOT NULL,
    upi            NVARCHAR(128)  NOT NULL,
    previous_upi   NVARCHAR(128)  NULL,
    change_type    NVARCHAR(32)   NOT NULL,
    trade_id       NVARCHAR(128)  NOT NULL,
    effective_date DATE           NOT NULL
);

CREATE INDEX ix_sm_upi_history_position ON sm_upi_history (position_key, effective_date);
