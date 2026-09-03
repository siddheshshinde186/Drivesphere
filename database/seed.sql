-- ===================================================================
-- DriveSphere – Smart Mobility & Fleet Intelligence Platform
-- Database Seed Data (seed.sql)
-- ===================================================================

USE drivesphere;

-- -------------------------------------------------------------------
-- 1. Insert Foundational Roles
-- -------------------------------------------------------------------
INSERT INTO roles (id, name, description) VALUES
(1, 'ROLE_ADMIN', 'Platform Super Administrator with system-wide access'),
(2, 'ROLE_DISPATCHER', 'Fleet Dispatcher managing vehicles, routes, and incidents'),
(3, 'ROLE_DRIVER', 'Fleet Driver fulfilling passenger trips or delivery dispatches'),
(4, 'ROLE_CUSTOMER', 'Standard customer or corporate mobility passenger')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- -------------------------------------------------------------------
-- 2. Insert Vehicle Categories
-- -------------------------------------------------------------------
INSERT INTO vehicle_categories (id, name, slug, description, base_rate_per_km, base_fare) VALUES
(1, 'Eco Sedan (Electric)', 'eco-sedan', 'High-efficiency 5-seater pure electric sedans for urban mobility', 15.00, 50.00),
(2, 'Fleet Executive EV', 'fleet-executive-ev', 'Premium long-range electric vehicles with advanced telematics', 24.00, 90.00),
(3, 'Smart Logistics Van', 'smart-logistics-van', 'Light commercial electric delivery van with high cargo volume', 20.00, 75.00),
(4, 'Autonomous Micro-Shuttle', 'autonomous-micro-shuttle', 'Last-mile urban electric shuttle for campus and smart city corridors', 12.00, 30.00)
ON DUPLICATE KEY UPDATE description = VALUES(description), base_rate_per_km = VALUES(base_rate_per_km), base_fare = VALUES(base_fare);

-- -------------------------------------------------------------------
-- 3. Insert Initial Test Vehicles
-- -------------------------------------------------------------------
INSERT INTO vehicles (id, vin, license_plate, category_id, make, model, manufacture_year, fuel_type, battery_capacity_kwh, status, current_odometer_km, current_latitude, current_longitude) VALUES
(1, 'DS-EV2026-00101', 'KA-01-EQ-1001', 1, 'DriveSphere Mobility', 'Aero EV3', 2026, 'ELECTRIC', 68.0, 'AVAILABLE', 4210.50, 12.9715987, 77.5945627),
(2, 'DS-EV2026-00102', 'KA-01-EQ-1002', 2, 'DriveSphere Mobility', 'Apex Pulse', 2026, 'ELECTRIC', 85.0, 'AVAILABLE', 2890.10, 12.9352000, 77.6245000),
(3, 'DS-EV2026-00103', 'KA-01-EQ-1003', 3, 'VoltCargo Tech', 'Vanguard Express', 2025, 'ELECTRIC', 75.0, 'CHARGING', 11450.80, 12.9279000, 77.6271000)
ON DUPLICATE KEY UPDATE status = VALUES(status), current_odometer_km = VALUES(current_odometer_km);
