-- Add effective_date and reference columns to supplier_ledger for manual outstanding adjustments
ALTER TABLE supplier_ledger
ADD COLUMN effective_date DATE,
ADD COLUMN reference VARCHAR(255);
