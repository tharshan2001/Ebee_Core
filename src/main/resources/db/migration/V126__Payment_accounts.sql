-- Bank and cash accounts: where money actually is. Balance = Σ IN − Σ OUT of its transactions
-- (an opening balance is an OPENING transaction).

CREATE TABLE payment_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL UNIQUE,
    type            VARCHAR(20)  NOT NULL CHECK (type IN ('CASH', 'BANK', 'CHEQUES_IN_HAND', 'WALLET')),
    bank_name       VARCHAR(100),
    account_number  VARCHAR(50),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE account_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID          NOT NULL REFERENCES payment_accounts(id),
    txn_date        DATE          NOT NULL,
    direction       VARCHAR(3)    NOT NULL CHECK (direction IN ('IN', 'OUT')),
    amount          NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    txn_type        VARCHAR(30)   NOT NULL,
    reference_type  VARCHAR(30),
    reference_id    UUID,
    -- Both legs of a transfer / cheque deposit share a group id.
    transfer_group  UUID,
    description     VARCHAR(500),
    recorded_by     UUID,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_account_txn_account_date ON account_transactions (account_id, txn_date);
CREATE INDEX idx_account_txn_type_date ON account_transactions (txn_type, txn_date);
CREATE INDEX idx_account_txn_reference ON account_transactions (reference_type, reference_id);

-- Which account a payment goes to when the caller doesn't pick one.
-- purpose = a payment method name (CASH, COD, BANK_TRANSFER, CARD_PAYMENT, CHEQUE)
--           or a flow (EXPENSE, COMMISSION_PAYOUT, REFUND).
CREATE TABLE payment_account_defaults (
    purpose     VARCHAR(30) PRIMARY KEY,
    account_id  UUID NOT NULL REFERENCES payment_accounts(id)
);

INSERT INTO payment_accounts (name, type) VALUES
    ('Cash in hand', 'CASH'),
    ('Main bank account', 'BANK'),
    ('Cheques in hand', 'CHEQUES_IN_HAND');

INSERT INTO payment_account_defaults (purpose, account_id)
SELECT p.purpose, a.id FROM (VALUES
    ('CASH', 'Cash in hand'),
    ('COD', 'Cash in hand'),
    ('REFUND', 'Cash in hand'),
    ('EXPENSE', 'Cash in hand'),
    ('BANK_TRANSFER', 'Main bank account'),
    ('CARD_PAYMENT', 'Main bank account'),
    ('COMMISSION_PAYOUT', 'Main bank account'),
    ('CHEQUE', 'Cheques in hand')
) AS p(purpose, account_name)
JOIN payment_accounts a ON a.name = p.account_name;
