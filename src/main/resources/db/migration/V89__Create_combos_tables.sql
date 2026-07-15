CREATE TABLE combos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_combos_is_active ON combos(is_active);

CREATE TABLE combo_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    combo_id UUID NOT NULL REFERENCES combos(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(combo_id, product_id)
);

CREATE INDEX idx_combo_items_combo_id ON combo_items(combo_id);
CREATE INDEX idx_combo_items_product_id ON combo_items(product_id);

CREATE TABLE combo_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    combo_id UUID NOT NULL UNIQUE REFERENCES combos(id) ON DELETE CASCADE,
    rule_type VARCHAR(20) NOT NULL CHECK (rule_type IN ('FREE_PRODUCT', 'DISCOUNT')),
    free_product_id UUID REFERENCES products(id),
    discount_type VARCHAR(10) CHECK (discount_type IN ('PERCENTAGE', 'FIXED')),
    discount_value DECIMAL(10,2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_free_product CHECK (
        rule_type != 'FREE_PRODUCT' OR free_product_id IS NOT NULL
    ),
    CONSTRAINT chk_discount CHECK (
        rule_type != 'DISCOUNT' OR (discount_type IS NOT NULL AND discount_value IS NOT NULL)
    )
);

CREATE INDEX idx_combo_rules_combo_id ON combo_rules(combo_id);
CREATE INDEX idx_combo_rules_free_product_id ON combo_rules(free_product_id);
