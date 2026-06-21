ALTER TABLE agents
    ALTER COLUMN commission_rate TYPE numeric(10,2),
    ALTER COLUMN total_sales TYPE numeric(12,2),
    ALTER COLUMN total_commission TYPE numeric(12,2);

ALTER TABLE commission_configs
    ALTER COLUMN commission_rate TYPE numeric(10,2),
    ALTER COLUMN min_amount TYPE numeric(12,2),
    ALTER COLUMN max_amount TYPE numeric(12,2);
