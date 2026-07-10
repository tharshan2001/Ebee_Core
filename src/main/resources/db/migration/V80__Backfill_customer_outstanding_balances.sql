-- Backfill customers.outstanding_balance for customers with unpaid/partially-paid orders
-- Some COD and legacy orders never incremented the customer's outstanding_balance

UPDATE customers c
SET outstanding_balance = COALESCE((
    SELECT SUM(o.outstanding_balance)
    FROM orders o
    WHERE o.customer_id = c.id
      AND o.payment_status IN ('UNPAID', 'PARTIALLY_PAID')
), 0)
WHERE id IN (
    SELECT DISTINCT o.customer_id
    FROM orders o
    WHERE o.payment_status IN ('UNPAID', 'PARTIALLY_PAID')
);
