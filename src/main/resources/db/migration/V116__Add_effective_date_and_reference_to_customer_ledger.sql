-- Add effective_date and reference columns to customer_ledger for manual outstanding adjustments
ALTER TABLE customer_ledger
ADD COLUMN effective_date DATE,
ADD COLUMN reference VARCHAR(255);
