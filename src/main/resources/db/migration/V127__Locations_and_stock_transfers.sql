-- Several business locations (warehouses / shops), stock per location, and stock transfers.
-- Everything that exists today moves to a single default "Main" location, so behaviour is
-- unchanged until a second location is added.

CREATE TABLE locations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL UNIQUE,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('WAREHOUSE', 'SHOP')),
    district    VARCHAR(10),
    address     VARCHAR(500),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
-- Exactly one default location.
CREATE UNIQUE INDEX uk_locations_single_default ON locations (is_default) WHERE is_default;

INSERT INTO locations (code, name, type, is_default) VALUES ('MAIN', 'Main', 'WAREHOUSE', TRUE);

-- Inventory: one row per (product, location) instead of per product.
ALTER TABLE inventory ADD COLUMN location_id UUID REFERENCES locations(id);
UPDATE inventory SET location_id = (SELECT id FROM locations WHERE is_default);
ALTER TABLE inventory ALTER COLUMN location_id SET NOT NULL;

DO $$
DECLARE c RECORD;
BEGIN
    -- Drop the old single-column UNIQUE(product_id) constraint, whatever it is named.
    FOR c IN
        SELECT con.conname FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'inventory' AND con.contype = 'u'
          AND array_length(con.conkey, 1) = 1
          AND con.conkey[1] = (SELECT attnum FROM pg_attribute WHERE attrelid = rel.oid AND attname = 'product_id')
    LOOP
        EXECUTE format('ALTER TABLE inventory DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;
ALTER TABLE inventory ADD CONSTRAINT uk_inventory_product_location UNIQUE (product_id, location_id);
CREATE INDEX idx_inventory_location ON inventory (location_id);

-- Where an order ships from / a PO is received into / an agent sells from by default.
ALTER TABLE orders          ADD COLUMN location_id UUID REFERENCES locations(id);
ALTER TABLE purchase_orders ADD COLUMN location_id UUID REFERENCES locations(id);
ALTER TABLE agents          ADD COLUMN location_id UUID REFERENCES locations(id);
UPDATE orders          SET location_id = (SELECT id FROM locations WHERE is_default);
UPDATE purchase_orders SET location_id = (SELECT id FROM locations WHERE is_default);

-- Stock transfers: DRAFT -> IN_TRANSIT (taken from source, FIFO cost recorded) -> RECEIVED
-- (added at destination at the same cost). CANCELLED from DRAFT or IN_TRANSIT.
CREATE TABLE stock_transfers (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_number   VARCHAR(30)  NOT NULL UNIQUE,
    from_location_id  UUID         NOT NULL REFERENCES locations(id),
    to_location_id    UUID         NOT NULL REFERENCES locations(id),
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('DRAFT', 'IN_TRANSIT', 'RECEIVED', 'CANCELLED')),
    notes             VARCHAR(500),
    created_by        UUID,
    dispatched_at     TIMESTAMP,
    received_at       TIMESTAMP,
    cancelled_at      TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    CHECK (from_location_id <> to_location_id)
);

CREATE TABLE stock_transfer_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id  UUID          NOT NULL REFERENCES stock_transfers(id) ON DELETE CASCADE,
    product_id   UUID          NOT NULL REFERENCES products(id),
    quantity     INTEGER       NOT NULL CHECK (quantity > 0),
    -- FIFO cost of the units taken from the source (set on dispatch).
    total_cost   NUMERIC(14,4)
);
CREATE INDEX idx_stock_transfer_items_transfer ON stock_transfer_items (transfer_id);
