CREATE TABLE delivery_trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    driver_id UUID REFERENCES drivers(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
    notes TEXT,
    mileage_km DECIMAL(10,2),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_delivery_trips_driver ON delivery_trips(driver_id);
CREATE INDEX idx_delivery_trips_status ON delivery_trips(status);
