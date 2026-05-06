-- Create commission_configs table
CREATE TABLE commission_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    commission_rate DOUBLE PRECISION NOT NULL,
    min_amount DOUBLE PRECISION DEFAULT 0.0,
    max_amount DOUBLE PRECISION,
    applies_to VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from TIMESTAMP NOT NULL,
    effective_to TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_commission_configs_active ON commission_configs(is_active);
CREATE INDEX idx_commission_configs_applies_to ON commission_configs(applies_to);

-- Create commissions table
CREATE TABLE commissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    config_id UUID REFERENCES commission_configs(id),
    order_amount DECIMAL(12, 2) NOT NULL,
    commission_rate DECIMAL(5, 2) NOT NULL,
    commission_amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    calculated_at TIMESTAMP,
    approved_at TIMESTAMP,
    paid_at TIMESTAMP,
    notes TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_commissions_agent_id ON commissions(agent_id);
CREATE INDEX idx_commissions_status ON commissions(status);
CREATE INDEX idx_commissions_order_id ON commissions(order_id);
CREATE UNIQUE INDEX idx_commissions_order_unique ON commissions(order_id);

-- Add check constraint for status values
ALTER TABLE commissions ADD CONSTRAINT chk_commission_status 
    CHECK (status IN ('PENDING', 'CALCULATED', 'APPROVED', 'PROCESSING', 'PAID', 'REJECTED', 'ERROR', 'CANCELLED'));
