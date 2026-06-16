-- Add max-discount fields to products (admin-set cap on per-order discounts).
-- Idempotent: safe to re-run if columns already exist.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'products' AND column_name = 'max_discount_type') THEN
        ALTER TABLE products
            ADD COLUMN max_discount_type VARCHAR(10),
            ADD COLUMN max_discount_value NUMERIC(10, 2);
    END IF;
END$$;

-- Add per-item discount fields to order_items (admin-applied at order time).
-- Idempotent: safe to re-run if columns already exist.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'order_items' AND column_name = 'discount_type') THEN
        ALTER TABLE order_items
            ADD COLUMN discount_type VARCHAR(10),
            ADD COLUMN discount_value NUMERIC(10, 2);
    END IF;
END$$;

-- Comments (idempotent)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'products' AND column_name = 'max_discount_type') THEN
        COMMENT ON COLUMN products.max_discount_type IS 'Optional cap on per-order discount. PERCENTAGE or FIXED. When NULL, per-order discounts are not allowed.';
        COMMENT ON COLUMN products.max_discount_value IS 'Optional max discount value (percentage 0-100 or fixed amount up to price)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'order_items' AND column_name = 'discount_type') THEN
        COMMENT ON COLUMN order_items.discount_type IS 'Per-item discount applied at order time. NULL = no per-order discount applied';
        COMMENT ON COLUMN order_items.discount_value IS 'Per-item discount value, must be <= products.max_discount_value';
    END IF;
END$$;
