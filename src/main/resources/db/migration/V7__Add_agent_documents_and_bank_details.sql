-- Add NIC and Bank Passbook document fields to agents table
ALTER TABLE agents ADD COLUMN nic_number VARCHAR(20);
ALTER TABLE agents ADD COLUMN nic_document BYTEA;
ALTER TABLE agents ADD COLUMN nic_document_name VARCHAR(255);
ALTER TABLE agents ADD COLUMN nic_document_type VARCHAR(100);

ALTER TABLE agents ADD COLUMN bank_passbook_document BYTEA;
ALTER TABLE agents ADD COLUMN bank_passbook_document_name VARCHAR(255);
ALTER TABLE agents ADD COLUMN bank_passbook_document_type VARCHAR(100);

ALTER TABLE agents ADD COLUMN bank_account_number VARCHAR(50);
ALTER TABLE agents ADD COLUMN bank_name VARCHAR(100);
ALTER TABLE agents ADD COLUMN bank_branch VARCHAR(100);

CREATE INDEX idx_agents_nic_number ON agents(nic_number);
