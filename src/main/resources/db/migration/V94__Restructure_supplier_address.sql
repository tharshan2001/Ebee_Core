ALTER TABLE suppliers ADD COLUMN address_line_1 VARCHAR(255);
ALTER TABLE suppliers ADD COLUMN address_line_2 VARCHAR(255);
ALTER TABLE suppliers ADD COLUMN city VARCHAR(100);
ALTER TABLE suppliers ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE suppliers ADD COLUMN district VARCHAR(50);
ALTER TABLE suppliers ADD COLUMN country VARCHAR(100) NOT NULL DEFAULT 'Sri Lanka';

UPDATE suppliers SET address_line_1 = address WHERE address IS NOT NULL;
UPDATE suppliers SET city = address_line_1 WHERE city IS NULL AND address_line_1 IS NOT NULL;

ALTER TABLE suppliers DROP COLUMN address;
