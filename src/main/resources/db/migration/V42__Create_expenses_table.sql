CREATE TABLE expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    description TEXT,
    receipt_url VARCHAR(500),
    date_incurred DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_by UUID NOT NULL REFERENCES users(id),
    driver_id UUID REFERENCES drivers(id),
    approved_by UUID REFERENCES users(id),
    approved_at TIMESTAMP,
    rejection_reason TEXT,
    paid_at TIMESTAMP,
    payment_reference VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_expenses_created_by ON expenses(created_by);
CREATE INDEX idx_expenses_driver_id ON expenses(driver_id);
CREATE INDEX idx_expenses_status ON expenses(status);
CREATE INDEX idx_expenses_category ON expenses(category);
CREATE INDEX idx_expenses_date_incurred ON expenses(date_incurred);

ALTER TABLE expenses ADD CONSTRAINT chk_expenses_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE expenses ADD CONSTRAINT chk_expenses_amount_positive
    CHECK (amount > 0);
