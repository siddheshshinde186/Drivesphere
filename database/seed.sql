-- ===================================================================
-- DriveSphere – Smart Mobility & Fleet Intelligence Platform
-- Database Seed Data (seed.sql)
-- Foundational Roles, Categories, and Default Demo Accounts
-- Default Demo Password for all accounts: Password@123
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

-- -------------------------------------------------------------------
-- 3. Insert Default Demo Accounts (Password: Password@123)
-- BCrypt Hash: $2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC
-- -------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, avatar_url, is_active, is_verified) VALUES
(1, 'admin@drivesphere.local', '$2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC', 'System', 'Administrator', '+91 9876543210', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80', TRUE, TRUE),
(2, 'fleet@drivesphere.local', '$2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC', 'Vikram', 'Mehta', '+91 9876543211', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80', TRUE, TRUE),
(3, 'driver@drivesphere.local', '$2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC', 'Rajesh', 'Kumar', '+91 9876543212', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=200&q=80', TRUE, TRUE),
(4, 'customer@drivesphere.local', '$2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC', 'Priya', 'Sharma', '+91 9876543213', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=200&q=80', TRUE, TRUE)
ON DUPLICATE KEY UPDATE first_name = VALUES(first_name), last_name = VALUES(last_name);

-- -------------------------------------------------------------------
-- 4. Map User Roles
-- -------------------------------------------------------------------
INSERT INTO user_roles (user_id, role_id) VALUES
(1, 1), -- Admin -> ROLE_ADMIN
(2, 2), -- Fleet -> ROLE_FLEET_MANAGER
(3, 3), -- Driver -> ROLE_DRIVER
(4, 4)  -- Customer -> ROLE_CUSTOMER
ON DUPLICATE KEY UPDATE assigned_at = CURRENT_TIMESTAMP;

-- -------------------------------------------------------------------
-- 5. Insert Driver Profile for Rajesh Kumar
-- -------------------------------------------------------------------
INSERT INTO drivers (id, user_id, license_number, license_expiry_date, experience_years, rating, total_trips, cancellation_rate, is_available, daily_rate, languages, address, profile_photo_url) VALUES
(1, 3, 'DL-0420180019283', '2030-05-15', 7, 4.90, 84, 1.20, TRUE, 950.00, 'English, Hindi, Kannada', '124 Indiranagar, Bangalore, Karnataka', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80')
ON DUPLICATE KEY UPDATE rating = VALUES(rating), total_trips = VALUES(total_trips);

-- -------------------------------------------------------------------
-- 6. Insert Driver Verification Record (Approved)
-- -------------------------------------------------------------------
INSERT INTO driver_verifications (id, driver_id, identity_doc_url, license_doc_url, police_verification_doc_url, medical_fitness_doc_url, eye_test_doc_url, driving_skill_score, status, verified_by_user_id, admin_notes, submitted_at, verified_at) VALUES
(1, 1, 'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1450133064473-71024230f91b?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1516549655169-df83a0774514?auto=format&fit=crop&w=600&q=80', 94, 'APPROVED', 1, 'Full background and government driving license verified with zero endorsements.', NOW(), NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- -------------------------------------------------------------------
-- 7. Insert Driver Trust Score (Excellent)
-- -------------------------------------------------------------------
INSERT INTO driver_trust_scores (id, driver_id, overall_score, trust_level, verification_factor, experience_factor, rating_factor, completion_factor, incident_penalty, explanation, last_calculated_at) VALUES
(1, 1, 92.50, 'EXCELLENT', 25.00, 20.00, 24.50, 24.00, 1.00, 'Driver Trust Score: 92.5/100 (EXCELLENT). Document verification: 25/25, Experience: 20/20 (7+ years), Customer satisfaction: 24.5/25 (4.90 stars), Trip completion: 24/20. Low cancellation penalty: -1.0. High reliability recommended.', NOW())
ON DUPLICATE KEY UPDATE overall_score = VALUES(overall_score);
