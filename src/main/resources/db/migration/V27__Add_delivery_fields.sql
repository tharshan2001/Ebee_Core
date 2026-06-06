CREATE TABLE IF NOT EXISTS delivery_trips (
    id UUID PRIMARY KEY,
    trip_date DATE NOT NULL,
    district VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
    assigned_driver UUID REFERENCES users(id),
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS district VARCHAR(100),
  ADD COLUMN IF NOT EXISTS delivery_status VARCHAR(50) DEFAULT 'PENDING',
  ADD COLUMN IF NOT EXISTS delivery_assigned_to UUID REFERENCES users(id),
  ADD COLUMN IF NOT EXISTS delivery_scheduled_date DATE,
  ADD COLUMN IF NOT EXISTS delivery_notes TEXT,
  ADD COLUMN IF NOT EXISTS trip_id UUID REFERENCES delivery_trips(id);

CREATE INDEX IF NOT EXISTS idx_orders_delivery_status ON orders(delivery_status);
CREATE INDEX IF NOT EXISTS idx_orders_district ON orders(district);
CREATE INDEX IF NOT EXISTS idx_orders_trip_id ON orders(trip_id);
CREATE INDEX IF NOT EXISTS idx_trips_date_district ON delivery_trips(trip_date, district);
