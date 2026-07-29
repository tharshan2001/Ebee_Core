ALTER TABLE orders ADD COLUMN trip_id UUID REFERENCES delivery_trips(id);
CREATE INDEX idx_orders_trip_id ON orders(trip_id);
