CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO categories (id, name)
SELECT gen_random_uuid(), category
FROM products
WHERE category IS NOT NULL AND category != ''
GROUP BY category
ORDER BY category;
