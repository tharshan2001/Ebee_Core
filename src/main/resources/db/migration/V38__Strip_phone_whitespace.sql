UPDATE customers SET phone_number = regexp_replace(phone_number, '\s+', '', 'g') WHERE phone_number ~ '\s';
UPDATE users SET phone_number = regexp_replace(phone_number, '\s+', '', 'g') WHERE phone_number ~ '\s';
UPDATE agents SET phone_number = regexp_replace(phone_number, '\s+', '', 'g') WHERE phone_number ~ '\s';
UPDATE agent_applications SET phone_number = regexp_replace(phone_number, '\s+', '', 'g') WHERE phone_number ~ '\s';

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
