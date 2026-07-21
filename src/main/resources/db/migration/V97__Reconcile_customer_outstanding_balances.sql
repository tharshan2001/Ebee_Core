-- Reconcile all customer outstanding_balances from their orders.
--
-- Previous fixes (V67, V80, V81, V83) filtered by payment_status,
-- but a COD order paid via CASH had its payment_status set to PAID
-- with outstanding_balance=0, so those orders were excluded from the
-- sum, leaving the customer balance stuck at an inflated value.
--
-- This sums EVERY order's outstanding_balance regardless of status.
-- For COD/CREDIT orders the customer balance was incremented at creation
-- and decremented with each payment, netting to the order's current
-- outstanding_balance.  CASH/BANK_TRANSFER/CARD orders are always PAID
-- with outstanding_balance=0, so they contribute zero.

UPDATE customers c
SET outstanding_balance = COALESCE((
    SELECT SUM(o.outstanding_balance)
    FROM orders o
    WHERE o.customer_id = c.id
), 0)
WHERE c.outstanding_balance != COALESCE((
    SELECT SUM(o.outstanding_balance)
    FROM orders o
    WHERE o.customer_id = c.id
), 0);
