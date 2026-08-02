-- Reconcile customer outstanding balances from order outstanding balances
-- This ensures customer.outstanding_balance matches the sum of their UNPAID/PARTIALLY_PAID orders

UPDATE customers c
SET outstanding_balance = COALESCE(
    (SELECT SUM(o.outstanding_balance)
     FROM orders o
     WHERE o.customer_id = c.id
     AND o.payment_status IN ('UNPAID', 'PARTIALLY_PAID')),
    0
);
