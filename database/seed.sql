-- ===================================================================
-- DriveSphere – Smart Mobility & Fleet Intelligence Platform
-- Production Database Seed Data (seed.sql)
-- Foundational Roles and Vehicle Categories ONLY
-- NO DEMO USERS OR PASSWORDS ARE SEEDED IN PRODUCTION
-- ===================================================================

USE drivesphere;

-- -------------------------------------------------------------------
-- 1. Insert Foundational Roles
-- -------------------------------------------------------------------
INSERT INTO roles (id, name, description) VALUES
(1, 'ROLE_ADMIN', 'Platform Administrator with system-wide governance'),
(2, 'ROLE_FLEET_MANAGER', 'Fleet Manager overseeing vehicles, telemetry, and maintenance'),
(3, 'ROLE_DRIVER', 'Professional driver with verification and Driver Trust Score'),
(4, 'ROLE_CUSTOMER', 'Standard customer booking self-drive or driver-accompanied vehicles')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- -------------------------------------------------------------------
-- 2. Insert Vehicle Categories
-- -------------------------------------------------------------------
INSERT INTO vehicle_categories (id, name, slug, description, base_rate_per_km, base_fare) VALUES
(1, 'Electric Mobility', 'electric-mobility', 'Zero-emission high-efficiency electric vehicles with smart telemetry', 14.00, 60.00),
(2, 'Executive Sedan', 'executive-sedan', 'Premium comfortable sedans for business commutes and highway travel', 16.00, 80.00),
(3, 'Adventure SUV', 'adventure-suv', 'Spacious 7-seater and rugged all-wheel drive SUVs for families and expeditions', 22.00, 120.00),
(4, 'Luxury Elite', 'luxury-elite', 'Top-tier luxury vehicles with prestige cabin appointments and VIP styling', 38.00, 250.00),
(5, 'Urban Hatchback', 'urban-hatchback', 'Compact, fuel-sipping vehicles engineered for nimble city navigation', 11.00, 40.00),
(6, 'Commercial Logistics & Shuttle', 'commercial-van', 'High-payload vans and passenger transit shuttles for corporate groups', 20.00, 150.00)
ON DUPLICATE KEY UPDATE description = VALUES(description), base_rate_per_km = VALUES(base_rate_per_km), base_fare = VALUES(base_fare);
