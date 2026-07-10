-- Set all RECEIVED purchase orders as UNPAID with full outstanding balance
UPDATE purchase_orders
SET payment_status = 'UNPAID',
    outstanding_balance = total_amount
WHERE status = 'RECEIVED'
  AND (payment_status IS NULL OR payment_status = 'UNPAID')
  AND total_amount > 0;

-- Sync supplier outstanding balances from their POs
UPDATE suppliers s
SET outstanding_balance = COALESCE((
    SELECT SUM(po.outstanding_balance)
    FROM purchase_orders po
    WHERE po.supplier_id = s.id
      AND po.status = 'RECEIVED'
), 0);
