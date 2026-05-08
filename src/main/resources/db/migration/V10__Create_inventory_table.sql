-- Create inventory table (new separate entity for stock levels)
CREATE TABLE inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL UNIQUE REFERENCES products(id),
    quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    reorder_level INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Migrate existing stock data from products table
INSERT INTO inventory (id, product_id, quantity, reorder_level, created_at, updated_at)
SELECT gen_random_uuid(), id, COALESCE(quantity_in_stock, 0), reorder_level, created_at, updated_at
FROM products;

-- Create indexes for inventory
CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_inventory_quantity ON inventory(quantity);
CREATE INDEX idx_inventory_reorder_level ON inventory(reorder_level);

-- Update stock table to reference inventory instead of products
-- First, add inventory_id column
ALTER TABLE stock ADD COLUMN inventory_id UUID;

-- Migrate product_id to inventory_id using a subquery to get the correct inventory id
UPDATE stock s
SET inventory_id = i.id
FROM inventory i
WHERE s.product_id = i.product_id;

-- Drop the old product_id column (after data is migrated)
ALTER TABLE stock DROP COLUMN product_id;

-- Add foreign key constraint (must reference inventory.id, not inventory.product_id)
ALTER TABLE stock ADD CONSTRAINT stock_inventory_id_fkey FOREIGN KEY (inventory_id) REFERENCES inventory(id);

-- Remove stock fields from products table
ALTER TABLE products DROP COLUMN IF EXISTS quantity_in_stock;
ALTER TABLE products DROP COLUMN IF EXISTS reorder_level;