-- Lot numbers and expiry dates on stock batches, earliest-expiry-first selling, and a record
-- of which batch each sale took from (for recalls and exact returns).

-- Products whose batches must carry an expiry date; they are sold earliest-expiry first and
-- expired batches are never sold.
ALTER TABLE products ADD COLUMN tracks_expiry BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE inventory_layers ADD COLUMN lot_number        VARCHAR(50);
ALTER TABLE inventory_layers ADD COLUMN expiry_date       DATE;
ALTER TABLE inventory_layers ADD COLUMN manufactured_date DATE;
CREATE INDEX idx_inventory_layers_expiry ON inventory_layers (expiry_date) WHERE remaining_quantity > 0;

-- Which batch (layer) each order line took its stock from.
CREATE TABLE order_item_lots (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_item_id     UUID          NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    product_id        UUID          NOT NULL REFERENCES products(id),
    layer_id          UUID          REFERENCES inventory_layers(id),
    lot_number        VARCHAR(50),
    expiry_date       DATE,
    quantity          INTEGER       NOT NULL CHECK (quantity > 0),
    unit_cost         NUMERIC(14,4) NOT NULL,
    -- Units of this lot that came back on returns / cancellation.
    returned_quantity INTEGER       NOT NULL DEFAULT 0 CHECK (returned_quantity >= 0),
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    CHECK (returned_quantity <= quantity)
);
CREATE INDEX idx_order_item_lots_item ON order_item_lots (order_item_id);
CREATE INDEX idx_order_item_lots_lot ON order_item_lots (product_id, lot_number);
