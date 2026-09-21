-- ===================================================================
-- DriveSphere – Smart Mobility & Fleet Intelligence Platform
-- Sample Data (sample-data.sql)
-- Realistic Fleet Vehicles, Bookings, Telemetry, Maintenance, and Reviews
-- ===================================================================

USE drivesphere;

-- -------------------------------------------------------------------
-- 1. Insert Diverse Fleet Vehicles
-- -------------------------------------------------------------------
INSERT INTO vehicles (id, registration_number, vin, brand, model, manufacture_year, vehicle_type, fuel_type, transmission, seating_capacity, mileage, battery_capacity_kwh, fuel_tank_capacity_l, rental_price_per_day, rental_price_per_hour, description, location, latitude, longitude, status, is_available, current_odometer_km, primary_image_url, category_id, average_rating, total_ratings) VALUES
(1, 'KA-01-EV-2024', 'VIN-TAT-NEXON-001', 'Tata', 'Nexon EV Empowered', 2024, 'ELECTRIC', 'ELECTRIC', 'AUTOMATIC', 5, 312.00, 40.5, NULL, 2800.00, 220.00, 'Long-range electric compact SUV with ventilated seats, electronic sunroof, and Level-1 ADAS safety.', 'Koramangala Hub, Bangalore', 12.9352, 77.6245, 'AVAILABLE', TRUE, 14200.00, 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=800&q=80', 1, 4.90, 18),
(2, 'KA-01-EV-2025', 'VIN-HYU-IONIQ-002', 'Hyundai', 'Ioniq 5 EV', 2024, 'ELECTRIC', 'ELECTRIC', 'AUTOMATIC', 5, 450.00, 72.6, NULL, 5200.00, 420.00, 'Futuristic ultra-fast charging electric crossover with parametric pixel styling and augmented reality HUD.', 'Indiranagar Hub, Bangalore', 12.9784, 77.6408, 'AVAILABLE', TRUE, 8900.00, 'https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=800&q=80', 1, 4.95, 24),
(3, 'KA-03-MG-4411', 'VIN-MAH-XUV-003', 'Mahindra', 'XUV700 AX7 Luxury', 2023, 'SUV', 'DIESEL', 'AUTOMATIC', 7, 14.80, NULL, 60.0, 3600.00, 290.00, 'Commanding 7-seater premium SUV with panoramic skyroof, dual 10.25-inch screens, and Sony 3D sound.', 'Whitefield Station, Bangalore', 12.9698, 77.7499, 'AVAILABLE', TRUE, 26400.00, 'https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=800&q=80', 3, 4.85, 32),
(4, 'KA-05-TR-9922', 'VIN-TOY-FORT-004', 'Toyota', 'Fortuner Legender 4x4', 2024, 'SUV', 'DIESEL', 'AUTOMATIC', 7, 12.20, NULL, 80.0, 4800.00, 380.00, 'Rugged expedition-ready 4WD flagship with cat-eye LED headlights, high ground clearance, and unmatched reliability.', 'MG Road Terminal, Bangalore', 12.9756, 77.6066, 'AVAILABLE', TRUE, 18300.00, 'https://images.unsplash.com/photo-1533473359331-0135ef1b58bf?auto=format&fit=crop&w=800&q=80', 3, 4.90, 40),
(5, 'KA-01-SD-3301', 'VIN-HON-CITY-005', 'Honda', 'City ZX e:HEV Hybrid', 2023, 'SEDAN', 'HYBRID', 'AUTOMATIC', 5, 27.10, NULL, 40.0, 2400.00, 190.00, 'Self-charging strong hybrid executive sedan delivering phenomenal fuel economy, smooth electric drive, and plush leather cabin.', 'Jayanagar Hub, Bangalore', 12.9308, 77.5838, 'AVAILABLE', TRUE, 22100.00, 'https://images.unsplash.com/photo-1552519507-da3b142c6e3d?auto=format&fit=crop&w=800&q=80', 2, 4.80, 15),
(6, 'KA-02-SK-7744', 'VIN-SKO-SLAV-006', 'Skoda', 'Slavia 1.5 TSI Style', 2024, 'SEDAN', 'PETROL', 'MANUAL', 5, 18.70, NULL, 45.0, 2200.00, 175.00, 'European engineering with spirited turbo performance, massive 521-liter boot, and 5-star Global NCAP safety.', 'Malleshwaram Hub, Bangalore', 13.0031, 77.5643, 'AVAILABLE', TRUE, 15400.00, 'https://images.unsplash.com/photo-1580273916550-e323be2ae537?auto=format&fit=crop&w=800&q=80', 2, 4.75, 12),
(7, 'KA-04-BM-8899', 'VIN-BMW-3SER-007', 'BMW', '330Li M Sport Gran Limousine', 2024, 'LUXURY', 'PETROL', 'AUTOMATIC', 5, 15.30, NULL, 59.0, 8500.00, 680.00, 'Executive extended-wheelbase sports saloon with Vernasca leather, panoramic glass roof, and BMW Curved Display.', 'UB City Luxury Concierge, Bangalore', 12.9716, 77.5957, 'AVAILABLE', TRUE, 9100.00, 'https://images.unsplash.com/photo-1555215695-3004980ad54e?auto=format&fit=crop&w=800&q=80', 4, 4.98, 29),
(8, 'KA-01-MB-1010', 'VIN-MB-ECLS-008', 'Mercedes-Benz', 'E-Class E220d Exclusive', 2023, 'LUXURY', 'DIESEL', 'AUTOMATIC', 5, 16.10, NULL, 66.0, 9200.00, 750.00, 'The pinnacle of corporate luxury mobility with rear reclining seats, Burmester 3D surround sound, and air suspension.', 'Airport Expressway Hub, Bangalore', 13.1986, 77.7066, 'AVAILABLE', TRUE, 16800.00, 'https://images.unsplash.com/photo-1618843479313-40f8afb4b4d8?auto=format&fit=crop&w=800&q=80', 4, 4.96, 35),
(9, 'KA-03-HB-5522', 'VIN-MAR-SWFT-009', 'Maruti Suzuki', 'Swift ZXi+ AMT', 2024, 'HATCHBACK', 'PETROL', 'AUTOMATIC', 5, 24.80, NULL, 37.0, 1400.00, 120.00, 'Ultra-efficient city runabout with 9-inch smart play pro touchscreen, cruise control, and 6 airbags standard.', 'HSR Layout Hub, Bangalore', 12.9121, 77.6446, 'AVAILABLE', TRUE, 11200.00, 'https://images.unsplash.com/photo-1541899481282-d53bffe3c35d?auto=format&fit=crop&w=800&q=80', 5, 4.70, 22),
(10, 'KA-01-VN-9009', 'VIN-FRC-URBN-010', 'Force Motors', 'Urbania Luxury 12-Seater', 2024, 'VAN', 'DIESEL', 'MANUAL', 12, 11.50, NULL, 70.0, 6200.00, 500.00, 'High-roof wide-body executive commuter van with individual AC vents, reclining captain seats, and sealed monocoque chassis.', 'Electronic City Terminal, Bangalore', 12.8399, 77.6770, 'AVAILABLE', TRUE, 19500.00, 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=800&q=80', 6, 4.88, 19)
ON DUPLICATE KEY UPDATE rental_price_per_day = VALUES(rental_price_per_day), status = VALUES(status);

-- -------------------------------------------------------------------
-- 2. Insert Additional Verified & Pending Drivers
-- -------------------------------------------------------------------
-- User 5: Driver Amit Verma (Approved)
INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, avatar_url, is_active, is_verified) VALUES
(5, 'amit.driver@drivesphere.local', '$2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC', 'Amit', 'Verma', '+91 9876543215', 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=200&q=80', TRUE, TRUE),
(6, 'sunil.driver@drivesphere.local', '$2a$10$FOYFRmqmk.QkDq6CitsGLus1MKe4bRSeO4tj9mnNuP.NiUsE91/VC', 'Sunil', 'Gowda', '+91 9876543216', 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=200&q=80', TRUE, FALSE)
ON DUPLICATE KEY UPDATE first_name = VALUES(first_name);

INSERT INTO user_roles (user_id, role_id) VALUES (5, 3), (6, 3)
ON DUPLICATE KEY UPDATE assigned_at = CURRENT_TIMESTAMP;

INSERT INTO drivers (id, user_id, license_number, license_expiry_date, experience_years, rating, total_trips, cancellation_rate, is_available, daily_rate, languages, address, profile_photo_url) VALUES
(2, 5, 'DL-0920190054321', '2031-08-20', 5, 4.85, 62, 0.80, TRUE, 850.00, 'English, Hindi', 'Sector 2, HSR Layout, Bangalore', 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=400&q=80'),
(3, 6, 'DL-1120220088991', '2032-11-10', 3, 4.70, 14, 2.50, FALSE, 750.00, 'Kannada, English', 'Vijayanagar, Bangalore', 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=400&q=80')
ON DUPLICATE KEY UPDATE rating = VALUES(rating);

INSERT INTO driver_verifications (id, driver_id, identity_doc_url, license_doc_url, police_verification_doc_url, medical_fitness_doc_url, eye_test_doc_url, driving_skill_score, status, verified_by_user_id, admin_notes, submitted_at, verified_at) VALUES
(2, 2, 'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1450133064473-71024230f91b?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1516549655169-df83a0774514?auto=format&fit=crop&w=600&q=80', 91, 'APPROVED', 1, 'Verified identity, commercial vehicle license, and clean police verification certificate.', NOW(), NOW()),
(3, 3, 'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop&w=600&q=80', 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?auto=format&fit=crop&w=600&q=80', NULL, NULL, NULL, 78, 'UNDER_REVIEW', NULL, 'Awaiting upload of medical fitness certificate and police clearance.', NOW(), NULL)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO driver_trust_scores (id, driver_id, overall_score, trust_level, verification_factor, experience_factor, rating_factor, completion_factor, incident_penalty, explanation, last_calculated_at) VALUES
(2, 2, 88.20, 'EXCELLENT', 25.00, 17.50, 24.20, 22.00, 0.50, 'Driver Trust Score: 88.2/100 (EXCELLENT). High passenger satisfaction with 62 completed journeys and low cancellation rate.', NOW()),
(3, 3, 62.00, 'AVERAGE', 12.00, 10.50, 23.50, 16.00, 0.00, 'Driver Trust Score: 62.0/100 (AVERAGE). Document verification under review. Provisional academic safety score.', NOW())
ON DUPLICATE KEY UPDATE overall_score = VALUES(overall_score);

-- -------------------------------------------------------------------
-- 3. Insert Historical Completed Booking & Review
-- -------------------------------------------------------------------
INSERT INTO bookings (id, booking_reference, customer_id, vehicle_id, driver_id, start_date_time, end_date_time, pickup_location, drop_location, pickup_latitude, pickup_longitude, drop_latitude, drop_longitude, total_days, total_hours, rental_amount, driver_amount, tax_amount, total_amount, booking_status, payment_status, pickup_time, return_time, notes) VALUES
(1, 'DS-20260901-ABC123', 4, 1, 1, '2026-09-01 08:00:00', '2026-09-02 20:00:00', 'Koramangala, Bangalore', 'Mysore Palace Road, Mysore', 12.9352, 77.6245, 12.3051, 76.6551, 2, 36, 5600.00, 1900.00, 1350.00, 8850.00, 'COMPLETED', 'PAID', '2026-09-01 08:15:00', '2026-09-02 19:45:00', 'Seamless weekend excursion. Excellent EV charging stops.')
ON DUPLICATE KEY UPDATE booking_status = VALUES(booking_status);

INSERT INTO payments (id, booking_id, amount, currency, gateway, gateway_order_id, gateway_payment_id, status) VALUES
(1, 1, 8850.00, 'INR', 'MOCK', 'order_mock_demo_001', 'pay_mock_demo_001', 'PAID')
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO reviews (id, booking_id, customer_id, vehicle_id, driver_id, rating, vehicle_rating, driver_rating, comment) VALUES
(1, 1, 4, 1, 1, 5.00, 5.00, 5.00, 'Remarkable experience with the Tata Nexon EV. Rajesh was an exceptionally polite, punctual, and safe driver. Highly recommended!')
ON DUPLICATE KEY UPDATE rating = VALUES(rating);

-- -------------------------------------------------------------------
-- 4. Insert Sample Maintenance Records
-- -------------------------------------------------------------------
INSERT INTO maintenance (id, vehicle_id, service_type, service_date, next_service_date, description, cost, status, odometer_km, notes) VALUES
(1, 3, 'ROUTINE', '2026-08-15', '2026-11-15', 'Regular 25,000 km periodic maintenance: engine oil change, oil filter replacement, tire rotation, and brake fluid inspection.', 4500.00, 'COMPLETED', 25000.00, 'Vehicle inspected and certified in optimal condition.'),
(2, 4, 'INSPECTION', '2026-09-10', '2026-12-10', 'Quarterly suspension alignment and 4WD transfer case diagnostics ahead of monsoon highway season.', 2800.00, 'SCHEDULED', 18300.00, 'Preventive inspection scheduled at Authorized Service Center.')
ON DUPLICATE KEY UPDATE status = VALUES(status);
