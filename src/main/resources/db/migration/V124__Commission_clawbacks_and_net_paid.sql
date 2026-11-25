-- Commission clawbacks (decision D2): when an order is returned after its commission was paid,
-- the agent owes the difference back. It is recovered from their next commission payments.
ALTER TABLE agents      ADD COLUMN clawback_balance NUMERIC(12,2) NOT NULL DEFAULT 0;
-- How much of this commission was clawed back after payment (partial or full return).
ALTER TABLE commissions ADD COLUMN clawback_amount  NUMERIC(12,2) NOT NULL DEFAULT 0;
-- Cash actually paid out for this commission (commission_amount minus any clawback recovered
-- at payment time). NULL for commissions paid before this change: treat as commission_amount.
ALTER TABLE commissions ADD COLUMN paid_amount      NUMERIC(12,2);
