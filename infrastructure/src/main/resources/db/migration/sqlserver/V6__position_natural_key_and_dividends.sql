-- Position natural key (contract + security + direction) and dividend sibling tables.
-- Maps to DOBasketActDivOpenLotTable / DOBasketActDivClosingTable.

ALTER TABLE basket_activity ADD security_id NVARCHAR(255) NULL;

UPDATE basket_activity SET security_id = underlier_id WHERE security_id IS NULL;

CREATE INDEX idx_basket_activity_natural_key ON basket_activity(contract_id, security_id, direction);

CREATE TABLE basket_div_open_lot (
    lot_id NVARCHAR(64) NOT NULL PRIMARY KEY,
    activity_id NVARCHAR(64) NOT NULL,
    source_open_lot_id NVARCHAR(64) NULL,
    dividend_id NVARCHAR(255) NOT NULL,
    ex_date DATE NULL,
    pay_date DATE NULL,
    quantity DECIMAL(28, 8) NOT NULL,
    remaining_qty DECIMAL(28, 8) NOT NULL,
    rate DECIMAL(28, 8) NULL,
    amount DECIMAL(28, 8) NULL,
    currency NVARCHAR(16) NULL,
    CONSTRAINT fk_basket_div_open_activity FOREIGN KEY (activity_id) REFERENCES basket_activity(activity_id)
);

CREATE INDEX idx_basket_div_open_activity ON basket_div_open_lot(activity_id);

CREATE TABLE basket_div_closing_lot (
    closing_lot_id NVARCHAR(64) NOT NULL PRIMARY KEY,
    activity_id NVARCHAR(64) NOT NULL,
    opened_dividend_lot_id NVARCHAR(64) NOT NULL,
    dividend_id NVARCHAR(255) NOT NULL,
    closed_qty DECIMAL(28, 8) NOT NULL,
    amount DECIMAL(28, 8) NULL,
    pay_date DATE NULL,
    currency NVARCHAR(16) NULL,
    CONSTRAINT fk_basket_div_close_activity FOREIGN KEY (activity_id) REFERENCES basket_activity(activity_id)
);

CREATE INDEX idx_basket_div_close_activity ON basket_div_closing_lot(activity_id);

CREATE TABLE financial_contract (
    contract_id NVARCHAR(64) NOT NULL PRIMARY KEY,
    product_type NVARCHAR(32) NOT NULL,
    product_qualifier NVARCHAR(64) NULL,
    underlier_type NVARCHAR(32) NULL,
    underlier_id NVARCHAR(255) NULL,
    currency NVARCHAR(16) NULL,
    account NVARCHAR(255) NULL,
    book NVARCHAR(255) NULL,
    party1 NVARCHAR(255) NULL,
    party2 NVARCHAR(255) NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    tax_lot_method NVARCHAR(16) NOT NULL DEFAULT 'FIFO',
    product_json NVARCHAR(MAX) NULL,
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
