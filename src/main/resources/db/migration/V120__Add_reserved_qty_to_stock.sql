-- Track reserved quantity snapshots on stock history rows so reserve/release
-- movements are visible in history (physical quantity is unchanged by these).
ALTER TABLE stock
    ADD COLUMN reserved_quantity_before INTEGER,
    ADD COLUMN reserved_quantity_after  INTEGER;