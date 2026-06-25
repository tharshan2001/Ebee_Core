UPDATE orders
SET outstanding_balance = total_amount
WHERE payment_status = 'UNPAID'
  AND (outstanding_balance IS NULL OR outstanding_balance = 0)
  AND total_amount > 0;
