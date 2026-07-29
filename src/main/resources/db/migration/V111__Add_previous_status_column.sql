-- V111: Add missing previous_status column to accounting_period_audit
-- The AccountingPeriodAudit entity expects previous_status but V101 didn't create it
ALTER TABLE accounting_period_audit ADD COLUMN IF NOT EXISTS previous_status period_status;
