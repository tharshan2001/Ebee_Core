-- Manual migration script to apply the discount feature to the live database.
-- The live backend is currently running an old build that does not have the
-- V57 migration applied. This script adds the missing columns.
--
-- IMPORTANT: This script is idempotent. Re-running it is safe.
-- After running this script, you must also rebuild and restart the backend
-- (mvn package) for the Java code to use these new columns.
--
-- Run with:
--   psql -U <user> -d ebee_db -f db_manual_migration_discount.sql
--
-- Or via Docker:
--   docker exec -i ebee_db psql -U <user> -d ebee_db < db_manual_migration_discount.sql

BEGIN;

-- 1. Add max-discount fields to products
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS max_discount_type VARCHAR(10),
    ADD COLUMN IF NOT EXISTS max_discount_value NUMERIC(10, 2);

-- 2. Add per-item discount fields to order_items
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS discount_type VARCHAR(10),
    ADD COLUMN IF NOT EXISTS discount_value NUMERIC(10, 2);

-- 3. Add comments (optional but helpful)
COMMENT ON COLUMN products.max_discount_type IS 'Optional cap on per-order discount. PERCENTAGE or FIXED. When NULL, per-order discounts are not allowed.';
COMMENT ON COLUMN products.max_discount_value IS 'Optional max discount value (percentage 0-100 or fixed amount up to price)';
COMMENT ON COLUMN order_items.discount_type IS 'Per-item discount applied at order time. NULL = no per-order discount applied';
COMMENT ON COLUMN order_items.discount_value IS 'Per-item discount value, must be <= products.max_discount_value';

COMMIT;

-- Verification: list the new columns
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE (table_name = 'products' AND column_name IN ('max_discount_type', 'max_discount_value'))
   OR (table_name = 'order_items' AND column_name IN ('discount_type', 'discount_value'))
ORDER BY table_name, column_name;
