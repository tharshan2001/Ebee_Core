-- V101: Create accounting tables for fiscal years and periods
-- Prevents editing of past accounting periods

-- Create period_status enum
CREATE TYPE period_status AS ENUM ('OPEN', 'CLOSED', 'ARCHIVED');

-- Create fiscal_years table
CREATE TABLE fiscal_years (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    year integer UNIQUE NOT NULL CHECK (year >= 2000 AND year <= 2100),
    name varchar(50) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    is_closed boolean NOT NULL DEFAULT false,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fiscal_year_dates CHECK (start_date < end_date)
);

-- Create accounting_periods table
CREATE TABLE accounting_periods (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    fiscal_year_id uuid NOT NULL REFERENCES fiscal_years(id) ON DELETE CASCADE,
    month integer NOT NULL CHECK (month >= 1 AND month <= 12),
    year integer NOT NULL,
    status period_status NOT NULL DEFAULT 'OPEN',
    opened_at timestamp,
    closed_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_accounting_period_year_month UNIQUE (fiscal_year_id, month)
);

-- Create accounting_period_audit table
CREATE TABLE accounting_period_audit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    period_id uuid NOT NULL REFERENCES accounting_periods(id) ON DELETE CASCADE,
    action varchar(50) NOT NULL,
    old_status period_status,
    new_status period_status NOT NULL,
    reason varchar(255) NOT NULL,
    performed_by uuid REFERENCES users(id),
    performed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add foreign keys to journal_entries for period control
ALTER TABLE journal_entries ADD COLUMN fiscal_year_id uuid;
ALTER TABLE journal_entries ADD COLUMN period_id uuid;
ALTER TABLE journal_entries ADD CONSTRAINT fk_journal_entries_fiscal_year
    FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id);
ALTER TABLE journal_entries ADD CONSTRAINT fk_journal_entries_period
    FOREIGN KEY (period_id) REFERENCES accounting_periods(id);

-- Add foreign keys to general_ledger for period control
ALTER TABLE general_ledger ADD COLUMN fiscal_year_id uuid;
ALTER TABLE general_ledger ADD COLUMN period_id uuid;
ALTER TABLE general_ledger ADD CONSTRAINT fk_general_ledger_fiscal_year
    FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id);
ALTER TABLE general_ledger ADD CONSTRAINT fk_general_ledger_period
    FOREIGN KEY (period_id) REFERENCES accounting_periods(id);

-- Add auto_close_day to whitelist_config
ALTER TABLE whitelist_config ADD COLUMN auto_close_day integer DEFAULT 5;

-- Create placeholder fiscal year for existing/pre-system entries
INSERT INTO fiscal_years (id, year, name, start_date, end_date, is_closed, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 2000, 'Pre-System Period', '2000-01-01', '2099-12-31', false, CURRENT_TIMESTAMP);

INSERT INTO accounting_periods (id, fiscal_year_id, month, year, status, opened_at, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 1, 2000, 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Backfill existing journal entries with placeholder period
UPDATE journal_entries SET fiscal_year_id = '00000000-0000-0000-0000-000000000001',
                          period_id = '00000000-0000-0000-0000-000000000001'
WHERE fiscal_year_id IS NULL;

-- Backfill existing general_ledger entries with placeholder period
UPDATE general_ledger SET fiscal_year_id = '00000000-0000-0000-0000-000000000001',
                          period_id = '00000000-0000-0000-0000-000000000001'
WHERE fiscal_year_id IS NULL;

-- Seed current fiscal year (adjust year as needed)
INSERT INTO fiscal_years (year, name, start_date, end_date)
VALUES (2026, 'FY 2026', '2026-01-01', '2026-12-31')
ON CONFLICT (year) DO NOTHING;

-- Create indexes
CREATE INDEX idx_fiscal_years_year ON fiscal_years(year);
CREATE INDEX idx_fiscal_years_closed ON fiscal_years(is_closed);
CREATE INDEX idx_accounting_periods_fiscal_year ON accounting_periods(fiscal_year_id);
CREATE INDEX idx_accounting_periods_year_month ON accounting_periods(year, month);
CREATE INDEX idx_accounting_periods_status ON accounting_periods(status);
CREATE INDEX idx_accounting_period_audit_period ON accounting_period_audit(period_id);
CREATE INDEX idx_accounting_period_audit_performer ON accounting_period_audit(performed_by);
CREATE INDEX idx_journal_entries_fiscal_year ON journal_entries(fiscal_year_id);
CREATE INDEX idx_journal_entries_period ON journal_entries(period_id);
CREATE INDEX idx_general_ledger_fiscal_year ON general_ledger(fiscal_year_id);
CREATE INDEX idx_general_ledger_period ON general_ledger(period_id);
