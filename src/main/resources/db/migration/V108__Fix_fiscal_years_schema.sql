-- V108: Fix fiscal_years table to match entity
-- Renames: year->fiscal_year, is_closed->is_active
-- Drops: name column (not in entity)
-- Adds: updated_at column

ALTER TABLE fiscal_years RENAME COLUMN year TO fiscal_year;

ALTER TABLE fiscal_years DROP COLUMN IF EXISTS name;

ALTER TABLE fiscal_years RENAME COLUMN is_closed TO is_active;

ALTER TABLE fiscal_years ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Invert is_active logic: was is_closed (true=closed), now is_active (true=active)
-- So we need to flip the values
UPDATE fiscal_years SET is_active = NOT is_active;

-- Add NOT NULL constraint after data is corrected
ALTER TABLE fiscal_years ALTER COLUMN is_active SET NOT NULL;
