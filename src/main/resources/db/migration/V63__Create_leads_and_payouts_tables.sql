CREATE TABLE leads (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL,
    agent_name VARCHAR(255) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    email VARCHAR(255),
    product_interest VARCHAR(255) NOT NULL,
    note TEXT,
    submitted_at TIMESTAMP NOT NULL,
    converted_order_id UUID,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE payouts (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL,
    agent_name VARCHAR(255) NOT NULL,
    total_amount NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMP,
    pdf_url VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE payout_commission_ids (
    payout_id UUID NOT NULL REFERENCES payouts(id),
    commission_id UUID NOT NULL
);
