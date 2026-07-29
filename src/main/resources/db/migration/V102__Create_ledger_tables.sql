-- V102: Create ledger tables for customer, supplier, and agent
-- Implemented as immutable append-only ledgers

-- Create customer_ledger table
CREATE TABLE customer_ledger (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id uuid NOT NULL REFERENCES customers(id),
    order_id uuid REFERENCES orders(id),
    payment_id uuid REFERENCES payments(id),
    return_id uuid REFERENCES returns(id),
    transaction_type varchar(50) NOT NULL,
    debit decimal(12,2) DEFAULT 0 CHECK (debit >= 0),
    credit decimal(12,2) DEFAULT 0 CHECK (credit >= 0),
    description text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_customer_ledger_debit_credit CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
    )
);

-- Create customer_balance_cache table
CREATE TABLE customer_balance_cache (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id uuid UNIQUE NOT NULL REFERENCES customers(id),
    last_ledger_entry_id uuid REFERENCES customer_ledger(id),
    balance decimal(12,2) NOT NULL DEFAULT 0,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create supplier_ledger table
CREATE TABLE supplier_ledger (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id uuid NOT NULL REFERENCES suppliers(id),
    purchase_order_id uuid REFERENCES purchase_orders(id),
    expense_id uuid REFERENCES expenses(id),
    transaction_type varchar(50) NOT NULL,
    debit decimal(12,2) DEFAULT 0 CHECK (debit >= 0),
    credit decimal(12,2) DEFAULT 0 CHECK (credit >= 0),
    description text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_supplier_ledger_debit_credit CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
    )
);

-- Create supplier_balance_cache table
CREATE TABLE supplier_balance_cache (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id uuid UNIQUE NOT NULL REFERENCES suppliers(id),
    last_ledger_entry_id uuid REFERENCES supplier_ledger(id),
    balance decimal(12,2) NOT NULL DEFAULT 0,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create agent_ledger table
CREATE TABLE agent_ledger (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id uuid NOT NULL REFERENCES agents(id),
    commission_id uuid REFERENCES commissions(id),
    payout_id uuid REFERENCES payouts(id),
    transaction_type varchar(50) NOT NULL,
    debit decimal(12,2) DEFAULT 0 CHECK (debit >= 0),
    credit decimal(12,2) DEFAULT 0 CHECK (credit >= 0),
    description text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_agent_ledger_debit_credit CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
    )
);

-- Create agent_balance_cache table
CREATE TABLE agent_balance_cache (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id uuid UNIQUE NOT NULL REFERENCES agents(id),
    last_ledger_entry_id uuid REFERENCES agent_ledger(id),
    balance decimal(12,2) NOT NULL DEFAULT 0,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for customer_ledger
CREATE INDEX idx_customer_ledger_customer ON customer_ledger(customer_id);
CREATE INDEX idx_customer_ledger_customer_date ON customer_ledger(customer_id, created_at);
CREATE INDEX idx_customer_ledger_order ON customer_ledger(order_id);
CREATE INDEX idx_customer_ledger_payment ON customer_ledger(payment_id);
CREATE INDEX idx_customer_ledger_return ON customer_ledger(return_id);
CREATE INDEX idx_customer_ledger_type ON customer_ledger(transaction_type);
CREATE INDEX idx_customer_balance_cache_customer ON customer_balance_cache(customer_id);

-- Create indexes for supplier_ledger
CREATE INDEX idx_supplier_ledger_supplier ON supplier_ledger(supplier_id);
CREATE INDEX idx_supplier_ledger_supplier_date ON supplier_ledger(supplier_id, created_at);
CREATE INDEX idx_supplier_ledger_po ON supplier_ledger(purchase_order_id);
CREATE INDEX idx_supplier_ledger_expense ON supplier_ledger(expense_id);
CREATE INDEX idx_supplier_ledger_type ON supplier_ledger(transaction_type);
CREATE INDEX idx_supplier_balance_cache_supplier ON supplier_balance_cache(supplier_id);

-- Create indexes for agent_ledger
CREATE INDEX idx_agent_ledger_agent ON agent_ledger(agent_id);
CREATE INDEX idx_agent_ledger_agent_date ON agent_ledger(agent_id, created_at);
CREATE INDEX idx_agent_ledger_commission ON agent_ledger(commission_id);
CREATE INDEX idx_agent_ledger_payout ON agent_ledger(payout_id);
CREATE INDEX idx_agent_ledger_type ON agent_ledger(transaction_type);
CREATE INDEX idx_agent_balance_cache_agent ON agent_balance_cache(agent_id);

-- Initialize balance caches for existing customers
INSERT INTO customer_balance_cache (customer_id, balance, updated_at)
SELECT c.id, COALESCE(c.outstanding_balance, 0), CURRENT_TIMESTAMP
FROM customers c
WHERE NOT EXISTS (
    SELECT 1 FROM customer_balance_cache WHERE customer_id = c.id
);

-- Initialize balance caches for existing suppliers
INSERT INTO supplier_balance_cache (supplier_id, balance, updated_at)
SELECT s.id, COALESCE(s.outstanding_balance, 0), CURRENT_TIMESTAMP
FROM suppliers s
WHERE NOT EXISTS (
    SELECT 1 FROM supplier_balance_cache WHERE supplier_id = s.id
);

-- Initialize balance caches for existing agents
INSERT INTO agent_balance_cache (agent_id, balance, updated_at)
SELECT a.id, 0, CURRENT_TIMESTAMP
FROM agents a
WHERE NOT EXISTS (
    SELECT 1 FROM agent_balance_cache WHERE agent_id = a.id
);
