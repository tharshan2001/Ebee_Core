-- Re-link CALCULATED/APPROVED commissions for DEFAULT agents that were calculated
-- using the agent-level fallback rate (config_id IS NULL) to the current active
-- default commission config and recalculate their amounts.
WITH default_config AS (
    SELECT id, commission_rate
    FROM commission_configs
    WHERE type = 'DEFAULT' AND is_active = true
    ORDER BY effective_from DESC
    LIMIT 1
)
UPDATE commissions c
SET
    config_id = dc.id,
    commission_rate = ROUND(dc.commission_rate::numeric, 2),
    commission_amount = ROUND(c.order_amount * dc.commission_rate::numeric / 100, 2),
    updated_at = CURRENT_TIMESTAMP
FROM default_config dc
WHERE c.config_id IS NULL
  AND c.status IN ('CALCULATED', 'APPROVED')
  AND EXISTS (
      SELECT 1 FROM agents a
      WHERE a.id = c.agent_id AND a.commission_type = 'DEFAULT'
  );
