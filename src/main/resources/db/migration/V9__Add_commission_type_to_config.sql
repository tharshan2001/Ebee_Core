-- Add type column to commission_configs table
ALTER TABLE commission_configs ADD COLUMN type VARCHAR(50) NOT NULL DEFAULT 'DEFAULT';

-- Update existing records to have DEFAULT type
UPDATE commission_configs SET type = 'DEFAULT' WHERE type IS NULL;

-- Add check constraint for type values
ALTER TABLE commission_configs ADD CONSTRAINT chk_commission_type
    CHECK (type IN ('DEFAULT', 'PERFORMANCE'));

-- Add index for type column
CREATE INDEX idx_commission_configs_type ON commission_configs(type);
