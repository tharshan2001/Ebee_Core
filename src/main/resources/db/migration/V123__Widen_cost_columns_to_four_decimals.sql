-- Per-unit FIFO cost can have fractions of a cent (e.g. 2 units at 100 + 1 at 101 = 100.3333).
-- Store 4 decimals so cost of goods = cost_price x quantity stays accurate. Widening only; no data change.
ALTER TABLE order_items ALTER COLUMN cost_price TYPE NUMERIC(14,4);
ALTER TABLE inventory   ALTER COLUMN average_cost TYPE NUMERIC(14,4);
