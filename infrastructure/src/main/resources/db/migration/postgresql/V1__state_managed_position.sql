CREATE TABLE sm_position (
    position_key   VARCHAR(64)    NOT NULL PRIMARY KEY,
    region         VARCHAR(32)    NOT NULL,
    account        VARCHAR(64)    NOT NULL,
    instrument     VARCHAR(64)    NOT NULL,
    currency       VARCHAR(8)     NOT NULL,
    status         VARCHAR(16)    NOT NULL,
    upi            VARCHAR(128)   NULL,
    total_qty      NUMERIC(28, 8) NOT NULL,
    avg_price      NUMERIC(28, 8) NOT NULL,
    open_lot_count INT            NOT NULL,
    realized_pnl   NUMERIC(28, 8) NOT NULL,
    as_of_date     DATE           NULL,
    next_lot_seq   BIGINT         NOT NULL,
    version        BIGINT         NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL
);

CREATE TABLE sm_trade (
    trade_id        VARCHAR(128)   NOT NULL PRIMARY KEY,
    position_key    VARCHAR(64)    NOT NULL,
    activity_type   VARCHAR(16)    NOT NULL,
    quantity        NUMERIC(28, 8) NULL,
    price           NUMERIC(28, 8) NOT NULL,
    effective_date  DATE           NOT NULL,
    settlement_date DATE           NULL,
    region          VARCHAR(32)    NOT NULL,
    applied_at      TIMESTAMPTZ    NOT NULL,
    account         VARCHAR(64)    NULL,
    instrument      VARCHAR(64)    NULL,
    currency        VARCHAR(8)     NULL
);

CREATE INDEX ix_sm_trade_position_date ON sm_trade (position_key, effective_date);
CREATE INDEX ix_sm_trade_region_date ON sm_trade (region, effective_date);

CREATE TABLE sm_lot (
    lot_id            VARCHAR(64)    NOT NULL PRIMARY KEY,
    position_key      VARCHAR(64)    NOT NULL,
    parent_lot_id     VARCHAR(64)    NULL,
    opening_trade_id  VARCHAR(128)   NOT NULL,
    closing_trade_id  VARCHAR(128)   NULL,
    original_qty      NUMERIC(28, 8) NOT NULL,
    remaining_qty     NUMERIC(28, 8) NOT NULL,
    open_price        NUMERIC(28, 8) NOT NULL,
    close_price       NUMERIC(28, 8) NULL,
    realized_pnl      NUMERIC(28, 8) NOT NULL,
    status            VARCHAR(16)    NOT NULL,
    effective_date    DATE           NOT NULL,
    settlement_date   DATE           NULL,
    closed_on         DATE           NULL
);

CREATE INDEX ix_sm_lot_position ON sm_lot (position_key, status);

CREATE TABLE sm_position_daily (
    position_key    VARCHAR(64)    NOT NULL,
    business_date   DATE           NOT NULL,
    region          VARCHAR(32)    NOT NULL,
    total_qty       NUMERIC(28, 8) NOT NULL,
    avg_price       NUMERIC(28, 8) NOT NULL,
    open_lot_count  INT            NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    upi             VARCHAR(128)   NULL,
    realized_pnl    NUMERIC(28, 8) NOT NULL,
    CONSTRAINT pk_sm_position_daily PRIMARY KEY (position_key, business_date)
);

CREATE INDEX ix_sm_position_daily_region ON sm_position_daily (region, business_date);

CREATE TABLE sm_lot_daily (
    position_key    VARCHAR(64)    NOT NULL,
    lot_id          VARCHAR(64)    NOT NULL,
    business_date   DATE           NOT NULL,
    region          VARCHAR(32)    NOT NULL,
    remaining_qty   NUMERIC(28, 8) NOT NULL,
    tradeable_qty   NUMERIC(28, 8) NOT NULL,
    original_qty    NUMERIC(28, 8) NOT NULL,
    open_price      NUMERIC(28, 8) NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    settlement_date DATE           NULL,
    realized_pnl    NUMERIC(28, 8) NOT NULL,
    CONSTRAINT pk_sm_lot_daily PRIMARY KEY (position_key, lot_id, business_date)
);

CREATE INDEX ix_sm_lot_daily_region ON sm_lot_daily (region, business_date, status);

CREATE TABLE sm_position_history (
    history_id       VARCHAR(36)    NOT NULL PRIMARY KEY,
    position_key     VARCHAR(64)    NOT NULL,
    trade_id         VARCHAR(128)   NOT NULL,
    effective_date   DATE           NOT NULL,
    qty_before       NUMERIC(28, 8) NOT NULL,
    qty_after        NUMERIC(28, 8) NOT NULL,
    status_before    VARCHAR(16)    NULL,
    status_after     VARCHAR(16)    NOT NULL,
    open_price_after NUMERIC(28, 8) NOT NULL
);

CREATE INDEX ix_sm_position_history_position ON sm_position_history (position_key, effective_date);

CREATE TABLE sm_upi_history (
    history_id     VARCHAR(36)    NOT NULL PRIMARY KEY,
    position_key   VARCHAR(64)    NOT NULL,
    upi            VARCHAR(128)   NOT NULL,
    previous_upi   VARCHAR(128)   NULL,
    change_type    VARCHAR(32)    NOT NULL,
    trade_id       VARCHAR(128)   NOT NULL,
    effective_date DATE           NOT NULL
);

CREATE INDEX ix_sm_upi_history_position ON sm_upi_history (position_key, effective_date);
