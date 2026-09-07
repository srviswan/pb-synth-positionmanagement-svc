-- State-saving basket activity model (replaces event-store replay for the CDM path).
-- Maps to the firm's DOBasketActivity / Details / OpenLot / ClosingLot / Settlement tables.

CREATE TABLE basket_activity (
    activity_id NVARCHAR(64) NOT NULL PRIMARY KEY,
    contract_id NVARCHAR(64) NOT NULL,
    position_key NVARCHAR(255) NOT NULL,
    upi NVARCHAR(255) NULL,
    account NVARCHAR(255) NULL,
    book NVARCHAR(255) NULL,
    direction NVARCHAR(16) NOT NULL,
    product_type NVARCHAR(32) NOT NULL,
    product_qualifier NVARCHAR(64) NULL,
    underlier_type NVARCHAR(32) NULL,
    underlier_id NVARCHAR(255) NULL,
    currency NVARCHAR(16) NULL,
    tax_lot_method NVARCHAR(16) NOT NULL DEFAULT 'FIFO',
    position_status NVARCHAR(16) NOT NULL,
    realized_pnl DECIMAL(28, 8) NOT NULL DEFAULT 0,
    product_json NVARCHAR(MAX) NULL,
    version INT NOT NULL,
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

CREATE INDEX idx_basket_activity_contract ON basket_activity(contract_id, underlier_id, direction);
CREATE INDEX idx_basket_activity_position_key ON basket_activity(position_key);
CREATE INDEX idx_basket_activity_status ON basket_activity(position_status);

CREATE TABLE basket_activity_detail (
    detail_id NVARCHAR(64) NOT NULL PRIMARY KEY,
    activity_id NVARCHAR(64) NOT NULL,
    trade_id NVARCHAR(255) NOT NULL,
    underlier_id NVARCHAR(255) NULL,
    quantity DECIMAL(28, 8) NOT NULL,
    price DECIMAL(28, 8) NOT NULL,
    currency NVARCHAR(16) NULL,
    trade_date DATE NOT NULL,
    effective_date DATE NULL,
    settlement_date DATE NULL,
    allocation_status NVARCHAR(32) NOT NULL DEFAULT 'ALLOCATED',
    recorded_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_basket_detail_activity FOREIGN KEY (activity_id) REFERENCES basket_activity(activity_id)
);

CREATE INDEX idx_basket_detail_activity ON basket_activity_detail(activity_id);
CREATE INDEX idx_basket_detail_trade ON basket_activity_detail(trade_id);

CREATE TABLE basket_open_lot (
    lot_id NVARCHAR(64) NOT NULL PRIMARY KEY,
    activity_id NVARCHAR(64) NOT NULL,
    source_detail_id NVARCHAR(64) NULL,
    source_trade_id NVARCHAR(255) NULL,
    original_qty DECIMAL(28, 8) NOT NULL,
    remaining_qty DECIMAL(28, 8) NOT NULL,
    cost_basis DECIMAL(28, 8) NOT NULL,
    current_ref_price DECIMAL(28, 8) NULL,
    trade_date DATE NOT NULL,
    settlement_date DATE NULL,
    settled_qty DECIMAL(28, 8) NULL,
    acquisition_sequence INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_basket_open_lot_activity FOREIGN KEY (activity_id) REFERENCES basket_activity(activity_id)
);

CREATE INDEX idx_basket_open_lot_activity ON basket_open_lot(activity_id);

CREATE TABLE basket_closing_lot (
    closing_lot_id NVARCHAR(64) NOT NULL PRIMARY KEY,
    activity_id NVARCHAR(64) NOT NULL,
    opened_lot_id NVARCHAR(64) NOT NULL,
    closing_detail_id NVARCHAR(64) NULL,
    closing_trade_id NVARCHAR(255) NULL,
    closed_qty DECIMAL(28, 8) NOT NULL,
    close_price DECIMAL(28, 8) NOT NULL,
    cost_basis DECIMAL(28, 8) NULL,
    realized_pnl DECIMAL(28, 8) NOT NULL DEFAULT 0,
    trade_date DATE NOT NULL,
    settlement_date DATE NULL,
    CONSTRAINT fk_basket_closing_lot_activity FOREIGN KEY (activity_id) REFERENCES basket_activity(activity_id)
);

CREATE INDEX idx_basket_closing_lot_activity ON basket_closing_lot(activity_id);

CREATE TABLE basket_settlement (
    settlement_id NVARCHAR(64) NOT NULL PRIMARY KEY,
    activity_id NVARCHAR(64) NOT NULL,
    detail_id NVARCHAR(64) NOT NULL,
    trade_id NVARCHAR(255) NOT NULL,
    settlement_date DATE NULL,
    settled_qty DECIMAL(28, 8) NOT NULL,
    currency NVARCHAR(16) NULL,
    status NVARCHAR(32) NOT NULL DEFAULT 'SETTLED',
    CONSTRAINT fk_basket_settlement_activity FOREIGN KEY (activity_id) REFERENCES basket_activity(activity_id)
);

CREATE INDEX idx_basket_settlement_activity ON basket_settlement(activity_id);
