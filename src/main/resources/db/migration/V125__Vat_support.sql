-- VAT support. Everything defaults to "no tax", so existing data and totals are unchanged
-- until an admin turns on vat_registered in business_settings.

CREATE TABLE tax_rates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)  NOT NULL UNIQUE,
    rate        NUMERIC(5,2)  NOT NULL CHECK (rate >= 0 AND rate <= 100),
    is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);
INSERT INTO tax_rates (name, rate) VALUES ('VAT 18%', 18.00), ('Exempt', 0.00);

-- Single-row settings table (id is always 1).
CREATE TABLE business_settings (
    id                      SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    vat_registered          BOOLEAN      NOT NULL DEFAULT FALSE,
    vat_registration_number VARCHAR(50),
    prices_include_tax      BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW()
);
INSERT INTO business_settings (id) VALUES (1);

-- NULL = exempt / no rate assigned.
ALTER TABLE products  ADD COLUMN tax_rate_id UUID REFERENCES tax_rates(id);
ALTER TABLE customers ADD COLUMN vat_number VARCHAR(50);
ALTER TABLE suppliers ADD COLUMN vat_number VARCHAR(50);

-- Sales: unit_price stays the gross price charged per unit; unit_tax is the VAT inside it.
ALTER TABLE order_items ADD COLUMN tax_rate   NUMERIC(5,2);
ALTER TABLE order_items ADD COLUMN unit_tax   NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN tax_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE orders      ADD COLUMN tax_total  NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Purchases: unit_price stays the net cost (used for FIFO layers); unit_tax is input VAT on top.
ALTER TABLE purchase_order_items ADD COLUMN tax_rate NUMERIC(5,2);
ALTER TABLE purchase_order_items ADD COLUMN unit_tax NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE purchase_orders      ADD COLUMN tax_total NUMERIC(12,2) NOT NULL DEFAULT 0;

-- VAT journal: one row per taxable event, dated when it happened (credit notes reduce VAT in
-- the period they are issued, not the period of the original invoice).
CREATE TABLE vat_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_date      DATE          NOT NULL,
    direction       VARCHAR(10)   NOT NULL CHECK (direction IN ('OUTPUT', 'INPUT')),
    source_type     VARCHAR(30)   NOT NULL,
    source_id       UUID          NOT NULL,
    reference       VARCHAR(100),
    taxable_amount  NUMERIC(14,2) NOT NULL,
    tax_amount      NUMERIC(14,2) NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_vat_entries_date ON vat_entries (entry_date, direction);
CREATE INDEX idx_vat_entries_source ON vat_entries (source_type, source_id);
