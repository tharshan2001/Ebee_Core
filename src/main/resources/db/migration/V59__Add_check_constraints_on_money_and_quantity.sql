-- Defense-in-depth CHECK constraints on money and quantity columns.
-- Application-layer validation already prevents these, but a CHECK
-- constraint stops a manual SQL update or a future code path that
-- bypasses the service layer from inserting nonsense data.
--
-- Idempotent: skips when the constraint already exists.
DO $$
BEGIN
    -- order_items.quantity must be > 0
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name = 'order_items' AND constraint_name = 'order_items_quantity_positive') THEN
        ALTER TABLE order_items
            ADD CONSTRAINT order_items_quantity_positive CHECK (quantity > 0);
    END IF;

    -- order_items.subtotal must be >= 0
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name = 'order_items' AND constraint_name = 'order_items_subtotal_nonneg') THEN
        ALTER TABLE order_items
            ADD CONSTRAINT order_items_subtotal_nonneg CHECK (subtotal >= 0);
    END IF;

    -- order_items.unit_price must be >= 0
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name = 'order_items' AND constraint_name = 'order_items_unit_price_nonneg') THEN
        ALTER TABLE order_items
            ADD CONSTRAINT order_items_unit_price_nonneg CHECK (unit_price >= 0);
    END IF;

    -- orders.total_amount must be >= 0
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name = 'orders' AND constraint_name = 'orders_total_amount_nonneg') THEN
        ALTER TABLE orders
            ADD CONSTRAINT orders_total_amount_nonneg CHECK (total_amount >= 0);
    END IF;

    -- products.price must be >= 0
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name = 'products' AND constraint_name = 'products_price_nonneg') THEN
        ALTER TABLE products
            ADD CONSTRAINT products_price_nonneg CHECK (price >= 0);
    END IF;

    -- products.cost_price must be >= 0
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name = 'products' AND constraint_name = 'products_cost_price_nonneg') THEN
        ALTER TABLE products
            ADD CONSTRAINT products_cost_price_nonneg CHECK (cost_price >= 0);
    END IF;

    -- inventory.quantity must be >= 0
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name = 'inventory' AND constraint_name = 'inventory_quantity_nonneg') THEN
        ALTER TABLE inventory
            ADD CONSTRAINT inventory_quantity_nonneg CHECK (quantity >= 0);
    END IF;

    -- inventory.reserved_quantity must be >= 0 and <= quantity
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name = 'inventory' AND constraint_name = 'inventory_reserved_bounded') THEN
        ALTER TABLE inventory
            ADD CONSTRAINT inventory_reserved_bounded
            CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity);
    END IF;
END$$;
