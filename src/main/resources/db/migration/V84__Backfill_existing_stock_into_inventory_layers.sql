-- Backfill existing stock that was created before the inventory_layers table existed (V79).
-- Products with quantity > 0 but no layers would cause "Insufficient stock layers" errors
-- when trying to sell/consume via FIFO.

INSERT INTO inventory_layers (inventory_id, quantity, remaining_quantity, unit_cost, received_at, created_at)
SELECT
    i.id,
    i.quantity,
    i.quantity,
    COALESCE(i.average_cost, 0),
    NOW(),
    NOW()
FROM inventory i
WHERE i.quantity > 0
  AND NOT EXISTS (
    SELECT 1 FROM inventory_layers il WHERE il.inventory_id = i.id
);
