-- 1. Create global code_sequence table (reserved-code pool, PK guarantees uniqueness)
CREATE TABLE code_sequence (
    code VARCHAR(20) PRIMARY KEY
);

-- 2. Add customer_code column to customers (nullable for backfill first)
ALTER TABLE customers ADD COLUMN IF NOT EXISTS customer_code VARCHAR(20);

-- 3. Backfill existing agent codes into the global pool
INSERT INTO code_sequence (code)
SELECT agent_code FROM agents WHERE agent_code IS NOT NULL
ON CONFLICT DO NOTHING;

-- 4. Backfill existing agent_code_sequence district-based last-used values
--    so no newly generated codes collide with the old sequential format.
--    Since the old format was {DISTRICT}-{NNN}, we reserve codes
--    for the next 100 values to be safe.
INSERT INTO code_sequence (code)
SELECT seq.district || '-' || LPAD(generate_series::text, 3, '0')
FROM agent_code_sequence seq
CROSS JOIN LATERAL generate_series(seq.next_value, seq.next_value + 99)
ON CONFLICT DO NOTHING;

-- 5. Generate random codes for existing customers that don't have one yet.
--    District prefix is derived from the customer's primary address.
--    We loop in PL/pgSQL to guarantee uniqueness across all agents+customers.
DO $$
DECLARE
    cust RECORD;
    dist_code TEXT;
    rand_code TEXT;
    full_code TEXT;
    chars TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    i INT;
    new_codes TEXT[] := '{}';
BEGIN
    FOR cust IN
        SELECT c.id, a.district
        FROM customers c
        LEFT JOIN LATERAL (
            SELECT district FROM addresses
            WHERE customer_id = c.id AND district IS NOT NULL
            ORDER BY id DESC LIMIT 1
        ) a ON true
        WHERE c.customer_code IS NULL
        ORDER BY c.id
    LOOP
        dist_code := CASE cust.district
            WHEN 'Jaffna' THEN 'JF'
            WHEN 'Kilinochchi' THEN 'KN'
            WHEN 'Mannar' THEN 'MN'
            WHEN 'Mullaitivu' THEN 'ML'
            WHEN 'Vavuniya' THEN 'VN'
            WHEN 'Trincomalee' THEN 'TM'
            WHEN 'Batticaloa' THEN 'BC'
            WHEN 'Ampara' THEN 'AP'
            ELSE 'XX'
        END;

        <<code_loop>>
        LOOP
            rand_code := '';
            FOR i IN 1..6 LOOP
                rand_code := rand_code || substr(chars, floor(random() * 30 + 1)::int, 1);
            END LOOP;
            full_code := dist_code || '-' || rand_code;

            IF NOT EXISTS (SELECT 1 FROM code_sequence WHERE code = full_code)
               AND NOT (full_code = ANY(new_codes)) THEN
                EXIT code_loop;
            END IF;
        END LOOP;

        INSERT INTO code_sequence (code) VALUES (full_code);
        UPDATE customers SET customer_code = full_code WHERE id = cust.id;
        new_codes := array_append(new_codes, full_code);
    END LOOP;
END;
$$;

-- 6. Add unique constraint on customer_code
ALTER TABLE customers ADD CONSTRAINT uq_customers_customer_code UNIQUE (customer_code);

-- 7. Make customer_code NOT NULL now that all rows are backfilled
ALTER TABLE customers ALTER COLUMN customer_code SET NOT NULL;

-- 8. Drop the old per-district sequence table
DROP TABLE agent_code_sequence;
