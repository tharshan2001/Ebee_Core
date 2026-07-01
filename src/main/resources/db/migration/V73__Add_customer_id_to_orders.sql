ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_id UUID REFERENCES customers(id);
CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id);
