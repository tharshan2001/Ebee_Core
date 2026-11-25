-- Accounting periods could never be created or audited on PostgreSQL:
--  * status columns used the native enum type period_status, but the entities map PeriodStatus as
--    text (@Enumerated STRING), so every INSERT/UPDATE failed with
--    "column status is of type period_status but expression is of type character varying";
--  * accounting_period_audit.action is NOT NULL but nothing ever wrote it.
-- Since ledger postings create the month's period on first use, that broke the first posting of
-- every new month. Store the statuses as checked text like the rest of the schema.

ALTER TABLE accounting_periods ALTER COLUMN status DROP DEFAULT;
ALTER TABLE accounting_periods ALTER COLUMN status TYPE VARCHAR(20) USING status::text;
ALTER TABLE accounting_periods ALTER COLUMN status SET DEFAULT 'OPEN';
ALTER TABLE accounting_periods ADD CONSTRAINT chk_accounting_periods_status
    CHECK (status IN ('OPEN', 'CLOSED', 'ARCHIVED'));

ALTER TABLE accounting_period_audit ALTER COLUMN old_status TYPE VARCHAR(20) USING old_status::text;
ALTER TABLE accounting_period_audit ALTER COLUMN new_status TYPE VARCHAR(20) USING new_status::text;
ALTER TABLE accounting_period_audit ALTER COLUMN previous_status TYPE VARCHAR(20) USING previous_status::text;
ALTER TABLE accounting_period_audit ADD CONSTRAINT chk_accounting_period_audit_status
    CHECK (new_status IN ('OPEN', 'CLOSED', 'ARCHIVED')
           AND (previous_status IS NULL OR previous_status IN ('OPEN', 'CLOSED', 'ARCHIVED')));
ALTER TABLE accounting_period_audit ALTER COLUMN action SET DEFAULT 'STATUS_CHANGE';

DROP TYPE period_status;
