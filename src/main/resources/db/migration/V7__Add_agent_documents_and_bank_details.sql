-- Add NIC and Bank Passbook document fields to agents table
ALTER TABLE agents ADD COLUMN IF NOT EXISTS nic_number VARCHAR(20);
ALTER TABLE agents ADD COLUMN IF NOT EXISTS nic_document_url VARCHAR(500);
ALTER TABLE agents ADD COLUMN IF NOT EXISTS nic_document_name VARCHAR(255);
ALTER TABLE agents ADD COLUMN IF NOT EXISTS nic_document_type VARCHAR(100);

ALTER TABLE agents ADD COLUMN IF NOT EXISTS bank_passbook_document_url VARCHAR(500);
ALTER TABLE agents ADD COLUMN IF NOT EXISTS bank_passbook_document_name VARCHAR(255);
ALTER TABLE agents ADD COLUMN IF NOT EXISTS bank_passbook_document_type VARCHAR(100);

ALTER TABLE agents ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(50);
ALTER TABLE agents ADD COLUMN IF NOT EXISTS bank_name VARCHAR(100);
ALTER TABLE agents ADD COLUMN IF NOT EXISTS bank_branch VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_agents_nic_number ON agents(nic_number);
