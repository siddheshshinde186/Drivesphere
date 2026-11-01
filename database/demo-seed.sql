-- ===================================================================
-- DriveSphere – Development / Demo Seed Script
-- OPTIONAL: For local development and demonstration only.
-- NOT for production use.
-- Default Demo Password for accounts: Password@123
-- ===================================================================

USE drivesphere;

-- -------------------------------------------------------------------
-- 1. Insert Default Demo Accounts (Password: Password@123)
-- BCrypt Hash: $2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC
-- -------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, avatar_url, is_active, is_verified, email_verified) VALUES
(1, 'admin@drivesphere.local', '$2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC', 'System', 'Administrator', '+91 9876543210', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80', TRUE, TRUE, TRUE),
(2, 'fleet@drivesphere.local', '$2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC', 'Vikram', 'Mehta', '+91 9876543211', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80', TRUE, TRUE, TRUE),
(3, 'driver@drivesphere.local', '$2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC', 'Rajesh', 'Kumar', '+91 9876543212', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=200&q=80', TRUE, TRUE, TRUE),
(4, 'customer@drivesphere.local', '$2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC', 'Priya', 'Sharma', '+91 9876543213', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=200&q=80', TRUE, TRUE, TRUE)
ON DUPLICATE KEY UPDATE first_name = VALUES(first_name), last_name = VALUES(last_name), email_verified = VALUES(email_verified);

-- -------------------------------------------------------------------
-- 2. Map User Roles
-- -------------------------------------------------------------------
INSERT INTO user_roles (user_id, role_id) VALUES
(1, 1), -- Admin -> ROLE_ADMIN
(2, 2), -- Fleet -> ROLE_FLEET_MANAGER
(3, 3), -- Driver -> ROLE_DRIVER
(4, 4)  -- Customer -> ROLE_CUSTOMER
ON DUPLICATE KEY UPDATE assigned_at = CURRENT_TIMESTAMP;

-- -------------------------------------------------------------------
-- 3. Insert Driver Profile for Rajesh Kumar
-- -------------------------------------------------------------------
INSERT INTO drivers (id, user_id, license_number, license_expiry_date, experience_years, rating, total_trips, cancellation_rate, is_available, is_online, daily_rate, languages, address, profile_photo_url) VALUES
(1, 3, 'DL-0420180019283', '2030-05-15', 7, 4.90, 84, 1.20, TRUE, FALSE, 950.00, 'English, Hindi, Kannada', '124 Indiranagar, Bangalore, Karnataka', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80')
ON DUPLICATE KEY UPDATE rating = VALUES(rating), total_trips = VALUES(total_trips);

-- -------------------------------------------------------------------
-- 4. Insert Driver Verification Record (Approved)
-- -------------------------------------------------------------------
INSERT INTO driver_verifications (id, driver_id, identity_doc_url, license_doc_url, police_verification_doc_url, medical_fitness_doc_url, eye_test_doc_url, driving_skill_score, status, verified_by_user_id, admin_notes, submitted_at, verified_at) VALUES
(1, 1, 'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1450133064473-71024230f91b?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1516549655169-df83a0774514?auto=format&fit=crop&w=600&q=80', 94, 'APPROVED', 1, 'Full background and government driving license verified with zero endorsements.', NOW(), NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- -------------------------------------------------------------------
-- 5. Insert Driver Trust Score (Excellent)
-- -------------------------------------------------------------------
INSERT INTO driver_trust_scores (id, driver_id, overall_score, trust_level, verification_factor, experience_factor, rating_factor, completion_factor, incident_penalty, explanation, last_calculated_at) VALUES
(1, 1, 88.90, 'EXCELLENT', 25.00, 20.00, 24.50, 20.00, 0.60, 'Driver Trust Score: 88.9/100 (EXCELLENT). Breakdown: Document Verification (25.0/25), Professional Experience (20.0/20), Customer Feedback Rating (24.5/25), Trip Completion (20.0/20), Cancellation Deduction (-0.6). Note: Algorithmic academic reliability metric, not an official government license certification.', NOW())
ON DUPLICATE KEY UPDATE overall_score = VALUES(overall_score);
