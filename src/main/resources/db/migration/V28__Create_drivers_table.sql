CREATE TABLE IF NOT EXISTS drivers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    license_number VARCHAR(50),
    contact_number VARCHAR(20),
    availability VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_drivers_availability ON drivers(availability);
