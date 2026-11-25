-- Customer price groups (e.g. Wholesale) and units of measure (sell by the box, pack, dozen…).
-- Nothing changes until groups / units are set up: existing lines are 1 unit = 1 stock unit.

CREATE TABLE price_groups (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- A product's price for a group, per stock unit. No row = the product's normal price.
CREATE TABLE product_group_prices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    price_group_id  UUID          NOT NULL REFERENCES price_groups(id) ON DELETE CASCADE,
    price           NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    UNIQUE (product_id, price_group_id)
);

ALTER TABLE customers ADD COLUMN price_group_id UUID REFERENCES price_groups(id);

-- Pack names (Box, Pack, Dozen…). How many stock units are in a pack is per product.
CREATE TABLE units (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50) NOT NULL UNIQUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);
INSERT INTO units (name) VALUES ('Box'), ('Pack'), ('Dozen'), ('Carton'), ('Bundle');

-- A product sold in a pack: units_per_pack stock units, at an optional fixed pack price
-- (null = per-unit price × units_per_pack).
CREATE TABLE product_units (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    unit_id         UUID          NOT NULL REFERENCES units(id),
    units_per_pack  INTEGER       NOT NULL CHECK (units_per_pack > 1),
    price           NUMERIC(10,2) CHECK (price >= 0),
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    UNIQUE (product_id, unit_id)
);

-- Order lines are counted in the unit they were sold in: quantity = packs, unit_price = per pack.
-- Stock moves quantity × units_per_sales_unit. Existing lines: 1.
ALTER TABLE order_items ADD COLUMN sales_unit_id        UUID REFERENCES product_units(id);
ALTER TABLE order_items ADD COLUMN sales_unit_name      VARCHAR(50);
ALTER TABLE order_items ADD COLUMN units_per_sales_unit INTEGER NOT NULL DEFAULT 1 CHECK (units_per_sales_unit > 0);
