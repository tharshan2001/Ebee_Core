-- Recalculate all customer outstanding_balances from scratch
-- The previous incremental approach (add/subtract in OrderPaymentService)
-- caused permanent drift when COD payment decrements were missed.
-- This sets each customer's balance to the sum of their unpaid/partially-paid order outstanding balances.

UPDATE customers c
SET outstanding_balance = COALESCE((
    SELECT SUM(o.outstanding_balance)
    FROM orders o
    WHERE o.customer_id = c.id
      AND o.payment_status IN ('UNPAID', 'PARTIALLY_PAID')
), 0);
