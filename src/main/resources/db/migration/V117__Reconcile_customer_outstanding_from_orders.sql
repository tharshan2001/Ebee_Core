-- Reconcile customer outstanding balances from customer_ledger
-- This ensures customer.outstanding_balance matches the net ledger balance
-- Net balance = SUM(debit) - SUM(credit) from all ledger entries (INVOICE, ADJUSTMENT, PAYMENT, RETURN)

UPDATE customers c
SET outstanding_balance = COALESCE(
    (SELECT SUM(debit) - SUM(credit)
     FROM customer_ledger cl
     WHERE cl.customer_id = c.id),
    0
)
WHERE EXISTS (
    SELECT 1 FROM customer_ledger cl2 WHERE cl2.customer_id = c.id
);

-- Also set outstanding_balance to 0 for customers with no ledger entries
UPDATE customers c
SET outstanding_balance = 0
WHERE outstanding_balance IS NULL
OR outstanding_balance < 0
OR NOT EXISTS (SELECT 1 FROM customer_ledger cl WHERE cl.customer_id = c.id);
