-- Add original_unit_price to order_items so per-item discount notifications
-- (and the order view modal) can show the pre-discount price and savings.
-- For existing rows, back-derive from unit_price + stored discount:
--   * No discount  -> original = unit_price
--   * PERCENTAGE   -> original = unit_price / (1 - value/100)
--   * FIXED        -> original = unit_price + value
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'order_items' AND column_name = 'original_unit_price') THEN
        ALTER TABLE order_items
            ADD COLUMN original_unit_price NUMERIC(10, 2);

        UPDATE order_items
        SET original_unit_price = CASE
            WHEN discount_value IS NULL OR discount_value = 0 THEN unit_price
            WHEN discount_type = 'PERCENTAGE'
                THEN ROUND((unit_price / NULLIF(1 - discount_value / 100.0, 0))::numeric, 2)
            WHEN discount_type = 'FIXED'
                THEN unit_price + discount_value
            ELSE unit_price
        END
        WHERE original_unit_price IS NULL;
    END IF;
END$$;
