-- Normalize phone numbers: strip country code, keep leading-zero format.
-- First, strip whitespace from all phone numbers to normalize spacing.
UPDATE customers SET phone_number = regexp_replace(phone_number, '\s+', '', 'g') WHERE phone_number ~ '\s';
UPDATE users SET phone_number = regexp_replace(phone_number, '\s+', '', 'g') WHERE phone_number ~ '\s';
UPDATE agents SET phone_number = regexp_replace(phone_number, '\s+', '', 'g') WHERE phone_number ~ '\s';
UPDATE agent_applications SET phone_number = regexp_replace(phone_number, '\s+', '', 'g') WHERE phone_number ~ '\s';

-- First, resolve conflicts on customers (unique constraint).
-- Delete records whose normalized form already exists under a different ID.
WITH normalized AS (
    SELECT id,
           CASE
               WHEN phone_number ~ '^94[0-9]{9}$' THEN '0' || SUBSTRING(phone_number FROM 3)
               WHEN phone_number ~ '^\+94[0-9]{9}$' THEN '0' || SUBSTRING(phone_number FROM 4)
               WHEN phone_number ~ '^[1-9][0-9]{8}$' THEN '0' || phone_number
               ELSE phone_number
           END AS norm_phone
    FROM customers
    WHERE phone_number IS NOT NULL AND phone_number !~ '^0[0-9]{9}$'
)
DELETE FROM customers c
USING normalized n1, normalized n2
WHERE c.id = n1.id
  AND n1.id != n2.id
  AND n1.norm_phone = n2.norm_phone
  AND n1.id > n2.id;

-- Now update customers (no more conflicts).
UPDATE customers
SET phone_number =
    CASE
        WHEN phone_number ~ '^94[0-9]{9}$' THEN '0' || SUBSTRING(phone_number FROM 3)
        WHEN phone_number ~ '^\+94[0-9]{9}$' THEN '0' || SUBSTRING(phone_number FROM 4)
        WHEN phone_number ~ '^[1-9][0-9]{8}$' THEN '0' || phone_number
        ELSE phone_number
    END
WHERE phone_number IS NOT NULL AND phone_number !~ '^0[0-9]{9}$';

UPDATE users
SET phone_number =
    CASE
        WHEN phone_number ~ '^94[0-9]{9}$' THEN '0' || SUBSTRING(phone_number FROM 3)
        WHEN phone_number ~ '^\+94[0-9]{9}$' THEN '0' || SUBSTRING(phone_number FROM 4)
        WHEN phone_number ~ '^[1-9][0-9]{8}$' THEN '0' || phone_number
        ELSE phone_number
    END
WHERE phone_number IS NOT NULL AND phone_number !~ '^0[0-9]{9}$';

UPDATE agents
SET phone_number =
    CASE
        WHEN phone_number ~ '^94[0-9]{9}$' THEN '0' || SUBSTRING(phone_number FROM 3)
        WHEN phone_number ~ '^\+94[0-9]{9}$' THEN '0' || SUBSTRING(phone_number FROM 4)
        WHEN phone_number ~ '^[1-9][0-9]{8}$' THEN '0' || phone_number
        ELSE phone_number
    END
WHERE phone_number IS NOT NULL AND phone_number !~ '^0[0-9]{9}$';

UPDATE agent_applications
SET phone_number =
    CASE
        WHEN phone_number ~ '^94[0-9]{9}$' THEN '0' || SUBSTRING(phone_number FROM 3)
        WHEN phone_number ~ '^\+94[0-9]{9}$' THEN '0' || SUBSTRING(phone_number FROM 4)
        WHEN phone_number ~ '^[1-9][0-9]{8}$' THEN '0' || phone_number
        ELSE phone_number
    END
WHERE phone_number IS NOT NULL AND phone_number !~ '^0[0-9]{9}$';
