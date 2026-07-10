-- Sync all customers' outstanding_balance with their actual unpaid order totals
-- Handles cases where:
-- 1. COD order payments didn't decrement customer balance (recordPayment was CREDIT-only)
-- 2. Legacy orders with null payment_method never incremented customer balance
-- 3. Any other drift between orders and customer balance

UPDATE customers c
SET outstanding_balance = COALESCE((
    SELECT SUM(o.outstanding_balance)
    FROM orders o
    WHERE o.customer_id = c.id
      AND o.payment_status IN ('UNPAID', 'PARTIALLY_PAID')
), 0);
