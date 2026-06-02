ALTER TABLE agents
ADD COLUMN commission_type VARCHAR(50) NOT NULL DEFAULT 'DEFAULT';

CREATE INDEX idx_agents_commission_type ON agents(commission_type);
