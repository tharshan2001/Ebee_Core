-- Track returned quantity per order item
ALTER TABLE order_items ADD COLUMN returned_quantity INTEGER NOT NULL DEFAULT 0;

-- Allow multiple return requests per order (only one active at a time)
ALTER TABLE return_exchange_requests DROP CONSTRAINT return_exchange_requests_order_id_key;
CREATE UNIQUE INDEX idx_return_exchange_active
    ON return_exchange_requests(order_id)
    WHERE status NOT IN ('REJECTED');

-- Item-level detail for return requests
CREATE TABLE return_request_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_request_id UUID NOT NULL REFERENCES return_exchange_requests(id) ON DELETE CASCADE,
    order_item_id UUID NOT NULL REFERENCES order_items(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_refund_amount DECIMAL(12, 2) NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_return_request_items_request ON return_request_items(return_request_id);
CREATE INDEX idx_return_request_items_order_item ON return_request_items(order_item_id);
