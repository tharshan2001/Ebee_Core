ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50),
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(50) NOT NULL DEFAULT 'UNPAID',
    ADD COLUMN IF NOT EXISTS paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS outstanding_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_purchase_orders_payment_status ON purchase_orders(payment_status);
