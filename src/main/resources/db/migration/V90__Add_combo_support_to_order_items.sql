ALTER TABLE order_items ADD COLUMN combo_id UUID REFERENCES combos(id);
ALTER TABLE order_items ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE order_items ADD COLUMN is_free_product BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_order_items_combo_id ON order_items(combo_id);
CREATE INDEX idx_order_items_combo_free ON order_items(combo_id, is_free_product);
