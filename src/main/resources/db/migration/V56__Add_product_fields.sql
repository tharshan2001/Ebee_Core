ALTER TABLE products
    ADD COLUMN discount_type VARCHAR(10),
    ADD COLUMN discount_value DECIMAL(10, 2),
    ADD COLUMN brand VARCHAR(100),
    ADD COLUMN tags TEXT;
