-- Create product_images table
CREATE TABLE product_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    image_name VARCHAR(255),
    image_type VARCHAR(100),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_images_sort UNIQUE (product_id, sort_order)
);

CREATE INDEX idx_product_images_product_id ON product_images(product_id);
CREATE INDEX idx_product_images_sort ON product_images(sort_order);

-- Add constraint to limit 10 images per product
CREATE OR REPLACE FUNCTION check_product_image_limit()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT COUNT(*) FROM product_images WHERE product_id = NEW.product_id) > 10 THEN
        RAISE EXCEPTION 'Cannot have more than 10 images per product';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_product_image_limit
    BEFORE INSERT ON product_images
    FOR EACH ROW
    EXECUTE FUNCTION check_product_image_limit();
