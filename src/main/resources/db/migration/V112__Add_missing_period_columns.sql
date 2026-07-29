-- V112: Add missing columns to accounting_periods table
-- Entity expects columns that V101 didn't create
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS start_date date;
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS end_date date;
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS closed_by uuid REFERENCES users(id);
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS archived_at timestamp;
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS archived_by uuid REFERENCES users(id);
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS updated_at timestamp;
