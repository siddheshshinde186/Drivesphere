-- ===================================================================
-- DriveSphere Flyway Migration V6: User Preferences and Maintenance Baseline
-- ===================================================================

-- Add customer travel preferences to users table
ALTER TABLE users ADD COLUMN preferred_vehicle_type VARCHAR(50) NULL;
ALTER TABLE users ADD COLUMN driver_preference VARCHAR(50) NULL;

-- Add vehicle maintenance service baseline tracking to vehicles table
ALTER TABLE vehicles ADD COLUMN last_service_odometer_km DECIMAL(10, 2) NULL;
ALTER TABLE vehicles ADD COLUMN last_service_date DATE NULL;

