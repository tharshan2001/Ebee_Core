ALTER TABLE purchase_order_items
    ADD COLUMN received_quantity INTEGER,
    ADD COLUMN returned_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE purchase_orders
    ADD COLUMN received_at TIMESTAMP(6),
    ADD COLUMN returned_at TIMESTAMP(6);
