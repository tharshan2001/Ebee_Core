-- Create return_exchange_requests table
CREATE TABLE return_exchange_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    requested_amount DECIMAL(12, 2) NOT NULL CHECK (requested_amount > 0),
    approved_amount DECIMAL(12, 2),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    resolution_type VARCHAR(50),
    reason TEXT NOT NULL,
    notes TEXT,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_return_exchange_status ON return_exchange_requests(status);
CREATE INDEX idx_return_exchange_order ON return_exchange_requests(order_id);
CREATE INDEX idx_return_exchange_created ON return_exchange_requests(created_at);
