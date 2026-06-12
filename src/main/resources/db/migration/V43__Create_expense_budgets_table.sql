CREATE TABLE expense_budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(50) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    allocated_amount DECIMAL(12, 2) NOT NULL,
    spent_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_budgets_category_period ON expense_budgets(category, period_start);
CREATE INDEX idx_budgets_period_start ON expense_budgets(period_start);

ALTER TABLE expense_budgets ADD CONSTRAINT chk_budget_amount_positive
    CHECK (allocated_amount > 0);

ALTER TABLE expense_budgets ADD CONSTRAINT chk_budget_spent_non_negative
    CHECK (spent_amount >= 0);
