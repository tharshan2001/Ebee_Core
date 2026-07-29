-- V100: Add CHECK constraint to enforce product_id XOR combo_id on order_items
-- DBML notes are NOT enforced by PostgreSQL, must add actual constraint

ALTER TABLE order_items
ADD CONSTRAINT chk_order_items_product_or_combo
CHECK (
  (product_id IS NOT NULL AND combo_id IS NULL)
  OR
  (product_id IS NULL AND combo_id IS NOT NULL)
);
