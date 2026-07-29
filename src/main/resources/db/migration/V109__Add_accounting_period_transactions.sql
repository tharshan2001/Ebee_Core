-- V109: Add accounting_period_transactions table for automatic transaction tracking

CREATE TABLE accounting_period_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_id UUID NOT NULL,
    fiscal_year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    year INTEGER NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    debit DECIMAL(15, 2) NOT NULL DEFAULT 0,
    credit DECIMAL(15, 2) NOT NULL DEFAULT 0,
    reference_id UUID,
    reference_type VARCHAR(50),
    description VARCHAR(500),
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_period FOREIGN KEY (period_id) REFERENCES accounting_periods(id) ON DELETE CASCADE
);

CREATE INDEX idx_period_transactions_period ON accounting_period_transactions(period_id);
CREATE INDEX idx_period_transactions_year_month ON accounting_period_transactions(fiscal_year, month);
CREATE INDEX idx_period_transactions_reference ON accounting_period_transactions(reference_id, reference_type);
