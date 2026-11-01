-- ===================================================================
-- DriveSphere Flyway Migration V3: Production Core Seed (No Demo Accounts)
-- ===================================================================

INSERT INTO roles (name, description) VALUES
('ROLE_ADMIN', 'System Platform Administrator'),
('ROLE_FLEET_MANAGER', 'Fleet Operations & Maintenance Manager'),
('ROLE_DRIVER', 'Verified Professional Chauffeur'),
('ROLE_CUSTOMER', 'Registered Mobility Customer')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO vehicle_categories (name, slug, description, base_rate_per_km, base_fare) VALUES
('Electric Mobility', 'electric-mobility', 'Zero-emission high-efficiency electric vehicles with smart telemetry', 14.00, 60.00),
('Executive Sedan', 'executive-sedan', 'Premium comfortable sedans for business commutes and highway travel', 16.00, 80.00),
('Adventure SUV', 'adventure-suv', 'Spacious 7-seater and rugged all-wheel drive SUVs for families and expeditions', 22.00, 120.00),
('Luxury Elite', 'luxury-elite', 'Top-tier luxury vehicles with prestige cabin appointments and VIP styling', 38.00, 250.00),
('Urban Hatchback', 'urban-hatchback', 'Compact, fuel-sipping vehicles engineered for nimble city navigation', 11.00, 40.00),
('Commercial Logistics & Shuttle', 'commercial-van', 'High-payload vans and passenger transit shuttles for corporate groups', 20.00, 150.00)
ON DUPLICATE KEY UPDATE description = VALUES(description), base_rate_per_km = VALUES(base_rate_per_km), base_fare = VALUES(base_fare);
