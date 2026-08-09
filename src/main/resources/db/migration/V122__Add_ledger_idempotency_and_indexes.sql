-- Prevent duplicate customer ledger entries for the same order (idempotency)
CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_ledger_order_invoice
    ON customer_ledger(customer_id, order_id, transaction_type)
    WHERE order_id IS NOT NULL AND transaction_type = 'INVOICE';

-- Add indexes on effective_date for date-range queries
CREATE INDEX IF NOT EXISTS idx_customer_ledger_effective_date
    ON customer_ledger(effective_date)
    WHERE effective_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_supplier_ledger_effective_date
    ON supplier_ledger(effective_date)
    WHERE effective_date IS NOT NULL;

-- Add composite index on accounting_period_transactions for period reports
CREATE INDEX IF NOT EXISTS idx_period_transactions_type
    ON accounting_period_transactions(period_id, transaction_type);
