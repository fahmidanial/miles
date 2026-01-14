-- Miles Tracker Database Schema
-- PostgreSQL with JSON support

-- Create database (run this manually in AWS RDS console or psql)
-- CREATE DATABASE miles_tracker;

-- Connect to the database
-- \c miles_tracker;

-- Create routes table
CREATE TABLE IF NOT EXISTS routes (
    id UUID PRIMARY KEY,
    device_id TEXT NOT NULL UNIQUE,
    start_timestamp BIGINT NOT NULL,
    points JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create index for faster queries by device_id
CREATE INDEX IF NOT EXISTS idx_routes_device_id ON routes(device_id);

-- Create index for timestamp queries
CREATE INDEX IF NOT EXISTS idx_routes_timestamp ON routes(start_timestamp);

-- Create updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_routes_updated_at
    BEFORE UPDATE ON routes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Optional: Create a user for the application (run manually)
-- CREATE USER miles_app WITH PASSWORD 'your_secure_password';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON routes TO miles_app;
-- GRANT USAGE ON SCHEMA public TO miles_app;

-- Sample data (for testing)
-- INSERT INTO routes (id, device_id, start_timestamp, points) VALUES
-- ('550e8400-e29b-41d4-a716-446655440000', 'test-device-123', 1640995200000,
--  '[{"latitude": 37.7749, "longitude": -122.4194, "speed": 25.5, "timestamp": 1640995200000}]');

-- Query examples:
-- Get latest route for a device:
-- SELECT * FROM routes WHERE device_id = 'your-device-id' ORDER BY start_timestamp DESC LIMIT 1;

-- Get all routes for a device:
-- SELECT * FROM routes WHERE device_id = 'your-device-id' ORDER BY start_timestamp DESC;

-- Export route as JSON:
-- SELECT id, device_id, start_timestamp, points FROM routes WHERE device_id = 'your-device-id' LIMIT 1;
