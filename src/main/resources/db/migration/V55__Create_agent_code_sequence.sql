CREATE TABLE IF NOT EXISTS agent_code_sequence (
    district VARCHAR(50) PRIMARY KEY,
    next_value INTEGER NOT NULL DEFAULT 1
);

INSERT INTO agent_code_sequence (district, next_value)
SELECT DISTINCT
    SUBSTRING(a.agent_code FROM 1 FOR POSITION('-' IN a.agent_code) - 1),
    MAX(CAST(SUBSTRING(a.agent_code FROM POSITION('-' IN a.agent_code) + 1) AS INTEGER)) + 1
FROM agents a
WHERE a.district IS NOT NULL
  AND a.agent_code ~ '^(JF|KN|MN|ML|VN|TM|BC|AP)-[0-9]+$'
GROUP BY SUBSTRING(a.agent_code FROM 1 FOR POSITION('-' IN a.agent_code) - 1)
ON CONFLICT (district) DO NOTHING;
