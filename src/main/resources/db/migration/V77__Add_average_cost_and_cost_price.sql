ALTER TABLE inventory ADD COLUMN average_cost DECIMAL(12,2) NOT NULL DEFAULT 0;

ALTER TABLE order_items ADD COLUMN cost_price DECIMAL(12,2);

UPDATE inventory i
SET average_cost = COALESCE(p.cost_price, 0)
FROM products p
WHERE p.id = i.product_id;
