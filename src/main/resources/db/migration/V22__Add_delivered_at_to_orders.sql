ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMP;

CREATE INDEX idx_orders_delivered_at ON orders(delivered_at);
