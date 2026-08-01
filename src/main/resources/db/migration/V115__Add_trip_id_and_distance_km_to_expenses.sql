ALTER TABLE expenses ADD COLUMN trip_id UUID REFERENCES delivery_trips(id);
ALTER TABLE expenses ADD COLUMN distance_km DECIMAL(10,2);
CREATE INDEX idx_expenses_trip_id ON expenses(trip_id);
