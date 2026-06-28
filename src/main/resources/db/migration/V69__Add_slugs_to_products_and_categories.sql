ALTER TABLE categories ADD COLUMN slug VARCHAR(255);
ALTER TABLE products ADD COLUMN slug VARCHAR(255);

-- Backfill slugs for categories
UPDATE categories
SET slug = LOWER(REGEXP_REPLACE(TRIM(REGEXP_REPLACE(name, '[^a-zA-Z0-9 ]', '', 'g')), ' +', '-', 'g'))
WHERE slug IS NULL AND name IS NOT NULL;

-- Resolve duplicate category slugs by appending a suffix
UPDATE categories c
SET slug = c.slug || '-' || (
    SELECT COUNT(*) FROM categories c2
    WHERE c2.slug = c.slug AND c2.id < c.id
)::text
WHERE (
    SELECT COUNT(*) FROM categories c2 WHERE c2.slug = c.slug
) > 1;

-- Backfill slugs for products
UPDATE products
SET slug = LOWER(REGEXP_REPLACE(TRIM(REGEXP_REPLACE(name, '[^a-zA-Z0-9 ]', '', 'g')), ' +', '-', 'g'))
WHERE slug IS NULL AND name IS NOT NULL;

-- For products with null/blank name, use item_code as slug
UPDATE products
SET slug = LOWER(REGEXP_REPLACE(TRIM(REGEXP_REPLACE(item_code, '[^a-zA-Z0-9 ]', '', 'g')), ' +', '-', 'g'))
WHERE slug IS NULL AND item_code IS NOT NULL;

-- Resolve duplicate product slugs by appending a suffix
UPDATE products p
SET slug = p.slug || '-' || (
    SELECT COUNT(*) FROM products p2
    WHERE p2.slug = p.slug AND p2.id < p.id
)::text
WHERE (
    SELECT COUNT(*) FROM products p2 WHERE p2.slug = p.slug
) > 1;

ALTER TABLE categories ADD CONSTRAINT uk_categories_slug UNIQUE (slug);
ALTER TABLE products ADD CONSTRAINT uk_products_slug UNIQUE (slug);
