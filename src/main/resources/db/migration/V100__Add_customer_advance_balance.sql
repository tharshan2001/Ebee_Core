-- Add advance_balance column to customers
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS advance_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00;

-- Create customer_advance_transactions table
CREATE TABLE customer_advance_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL,
    amount DECIMAL(12,2) NOT NULL,
    balance_after DECIMAL(12,2) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    reference_type VARCHAR(30) NOT NULL,
    reference_id UUID,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_advance_tx_customer_id ON customer_advance_transactions(customer_id);
CREATE INDEX idx_advance_tx_order_id ON customer_advance_transactions(order_id);
CREATE INDEX idx_advance_tx_created_at ON customer_advance_transactions(created_at);
