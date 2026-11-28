-- Only name, phone and district are required for a customer; add an optional second phone.
ALTER TABLE customers ADD COLUMN phone_number_2 VARCHAR(20);
ALTER TABLE addresses ALTER COLUMN street DROP NOT NULL;
ALTER TABLE addresses ALTER COLUMN city DROP NOT NULL;
