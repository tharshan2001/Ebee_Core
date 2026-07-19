ALTER TABLE agents ADD COLUMN address_line_1 VARCHAR(255);
ALTER TABLE agents ADD COLUMN address_line_2 VARCHAR(255);
ALTER TABLE agents ADD COLUMN city VARCHAR(100);
ALTER TABLE agents ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE agents ADD COLUMN country VARCHAR(100) NOT NULL DEFAULT 'Sri Lanka';

UPDATE agents SET address_line_1 = address WHERE address IS NOT NULL;
UPDATE agents SET city = address_line_1 WHERE city IS NULL AND address_line_1 IS NOT NULL;

ALTER TABLE agents DROP COLUMN address;
