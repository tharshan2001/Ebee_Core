-- Add max-discount fields to products (admin-set cap on per-order discounts)
ALTER TABLE products
    ADD COLUMN max_discount_type VARCHAR(10),
    ADD COLUMN max_discount_value NUMERIC(10, 2);

-- Add per-item discount fields to order_items (admin-applied at order time)
ALTER TABLE order_items
    ADD COLUMN discount_type VARCHAR(10),
    ADD COLUMN discount_value NUMERIC(10, 2);

-- Comments
COMMENT ON COLUMN products.max_discount_type IS 'Optional cap on per-order discount. PERCENTAGE or FIXED. When NULL, per-order discounts are not allowed.';
COMMENT ON COLUMN products.max_discount_value IS 'Optional max discount value (percentage 0-100 or fixed amount up to price)';
COMMENT ON COLUMN order_items.discount_type IS 'Per-item discount applied at order time. NULL = no per-order discount applied';
COMMENT ON COLUMN order_items.discount_value IS 'Per-item discount value, must be <= products.max_discount_value';
