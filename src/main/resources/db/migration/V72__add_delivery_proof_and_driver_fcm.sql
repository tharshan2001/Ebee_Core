ALTER TABLE orders
    ADD COLUMN delivery_photo_url VARCHAR(500),
    ADD COLUMN delivery_signature_url VARCHAR(500),
    ADD COLUMN driver_notes TEXT;

ALTER TABLE drivers
    ADD COLUMN fcm_token VARCHAR(500);
