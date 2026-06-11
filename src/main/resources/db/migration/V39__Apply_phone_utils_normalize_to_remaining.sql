-- Apply PhoneUtils.normalize() logic to remaining non-standard phone numbers.
-- No functions, all inline.

-- agent_applications: no unique constraint, update directly.
UPDATE agent_applications
SET phone_number =
    CASE
        WHEN LENGTH(regexp_replace(phone_number, '[^0-9]', '', 'g')) > 10
             AND regexp_replace(phone_number, '[^0-9]', '', 'g') ~ '^94'
        THEN '0' || SUBSTRING(regexp_replace(phone_number, '[^0-9]', '', 'g') FROM 3)
        WHEN regexp_replace(phone_number, '[^0-9]', '', 'g') !~ '^0'
        THEN '0' || regexp_replace(phone_number, '[^0-9]', '', 'g')
        ELSE regexp_replace(phone_number, '[^0-9]', '', 'g')
    END
WHERE phone_number IS NOT NULL AND phone_number !~ '^0[0-9]{9}$';

-- customers: handle duplicates by appending unique suffix.
UPDATE customers c
SET phone_number = n.unique_phone
FROM (
    SELECT id,
           CASE
               WHEN LENGTH(rr) > 10 AND rr ~ '^94' THEN '0' || SUBSTRING(rr FROM 3)
               WHEN rr !~ '^0' THEN '0' || rr
               ELSE rr
           END || CASE rn WHEN 1 THEN '' ELSE SUBSTRING(md5(random()::text) FOR 1) END AS unique_phone
    FROM (
        SELECT id, regexp_replace(phone_number, '[^0-9]', '', 'g') AS rr,
               ROW_NUMBER() OVER (
                   PARTITION BY CASE
                       WHEN LENGTH(regexp_replace(phone_number, '[^0-9]', '', 'g')) > 10
                            AND regexp_replace(phone_number, '[^0-9]', '', 'g') ~ '^94'
                       THEN '0' || SUBSTRING(regexp_replace(phone_number, '[^0-9]', '', 'g') FROM 3)
                       WHEN regexp_replace(phone_number, '[^0-9]', '', 'g') !~ '^0'
                       THEN '0' || regexp_replace(phone_number, '[^0-9]', '', 'g')
                       ELSE regexp_replace(phone_number, '[^0-9]', '', 'g')
                   END
                   ORDER BY id
               ) AS rn
        FROM customers
        WHERE phone_number IS NOT NULL AND phone_number !~ '^0[0-9]{9}$'
    ) sub
) n
WHERE c.id = n.id;
