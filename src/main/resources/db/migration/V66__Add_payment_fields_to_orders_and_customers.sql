-- Add payment fields to orders table
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50),
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(50) NOT NULL DEFAULT 'UNPAID',
    ADD COLUMN IF NOT EXISTS paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS outstanding_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00;

-- Add credit fields to customers table
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS credit_limit DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS outstanding_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00;

-- Create index on payment_status for reporting
CREATE INDEX IF NOT EXISTS idx_orders_payment_status ON orders(payment_status);
