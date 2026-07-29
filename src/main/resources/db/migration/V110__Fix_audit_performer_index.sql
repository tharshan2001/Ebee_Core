-- V110: Fix incorrect index on accounting_period_audit table
-- V101 created an index on 'performed_by' but the column is actually 'changed_by'
CREATE INDEX IF NOT EXISTS idx_accounting_period_audit_performer ON accounting_period_audit(changed_by);
