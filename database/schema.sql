-- ===================================================================
-- DriveSphere – Smart Mobility & Fleet Intelligence Platform
-- Target RDBMS: MySQL 8.x
-- Database Schema Definition (schema.sql)
-- ===================================================================

CREATE DATABASE IF NOT EXISTS drivesphere
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE drivesphere;

-- -------------------------------------------------------------------
-- 1. Roles Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 2. Users Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(60) NOT NULL,
    last_name VARCHAR(60) NOT NULL,
    phone_number VARCHAR(20) UNIQUE,
    avatar_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email),
    INDEX idx_users_phone (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 3. User Roles Mapping Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 4. Vehicle Categories Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vehicle_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(60) NOT NULL UNIQUE,
    slug VARCHAR(60) NOT NULL UNIQUE,
    description TEXT,
    base_rate_per_km DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    base_fare DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 5. Vehicles Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_number VARCHAR(30) NOT NULL UNIQUE,
    vin VARCHAR(50) UNIQUE,
    brand VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    manufacture_year INT NOT NULL,
    vehicle_type ENUM('SEDAN', 'SUV', 'HATCHBACK', 'ELECTRIC', 'LUXURY', 'VAN') NOT NULL,
    fuel_type ENUM('PETROL', 'DIESEL', 'ELECTRIC', 'HYBRID') NOT NULL,
    transmission ENUM('AUTOMATIC', 'MANUAL') NOT NULL,
    seating_capacity INT NOT NULL,
    mileage DECIMAL(6, 2) NOT NULL,
    battery_capacity_kwh DECIMAL(6, 2) NULL,
    fuel_tank_capacity_l DECIMAL(6, 2) NULL,
    rental_price_per_day DECIMAL(10, 2) NOT NULL,
    rental_price_per_hour DECIMAL(10, 2) NOT NULL,
    description TEXT,
    location VARCHAR(150),
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    status ENUM('AVAILABLE', 'BOOKED', 'RENTED', 'MAINTENANCE', 'INACTIVE') NOT NULL DEFAULT 'AVAILABLE',
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    current_odometer_km DECIMAL(10, 2) DEFAULT 0.00,
    primary_image_url VARCHAR(500),
    category_id BIGINT NULL,
    average_rating DECIMAL(3, 2) DEFAULT 5.00,
    total_ratings INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicles_category FOREIGN KEY (category_id) REFERENCES vehicle_categories(id) ON DELETE SET NULL,
    INDEX idx_vehicles_status (status),
    INDEX idx_vehicles_reg (registration_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 6. Vehicle Images Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vehicle_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicle_images_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 7. Drivers Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS drivers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    license_number VARCHAR(50) NOT NULL UNIQUE,
    license_expiry_date DATE NOT NULL,
    experience_years INT NOT NULL DEFAULT 0,
    rating DECIMAL(3, 2) DEFAULT 5.00,
    total_trips INT DEFAULT 0,
    cancellation_rate DECIMAL(5, 2) DEFAULT 0.00,
    is_available BOOLEAN DEFAULT TRUE,
    daily_rate DECIMAL(10, 2) NOT NULL DEFAULT 800.00,
    languages VARCHAR(150),
    address VARCHAR(255),
    profile_photo_url VARCHAR(500),
    current_vehicle_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_drivers_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_drivers_vehicle FOREIGN KEY (current_vehicle_id) REFERENCES vehicles(id) ON DELETE SET NULL,
    INDEX idx_drivers_available (is_available)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 8. Driver Verifications Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS driver_verifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT NOT NULL UNIQUE,
    identity_doc_url VARCHAR(500),
    license_doc_url VARCHAR(500),
    police_verification_doc_url VARCHAR(500),
    medical_fitness_doc_url VARCHAR(500),
    eye_test_doc_url VARCHAR(500),
    driving_skill_score INT DEFAULT 85,
    status ENUM('PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    verified_by_user_id BIGINT NULL,
    admin_notes TEXT,
    submitted_at TIMESTAMP NULL,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_verif_driver FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE CASCADE,
    CONSTRAINT fk_verif_admin FOREIGN KEY (verified_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_verif_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 9. Driver Trust Scores Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS driver_trust_scores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT NOT NULL UNIQUE,
    overall_score DECIMAL(5, 2) NOT NULL DEFAULT 80.00,
    trust_level ENUM('EXCELLENT', 'GOOD', 'AVERAGE', 'LOW') NOT NULL DEFAULT 'GOOD',
    verification_factor DECIMAL(5, 2) DEFAULT 25.00,
    experience_factor DECIMAL(5, 2) DEFAULT 15.00,
    rating_factor DECIMAL(5, 2) DEFAULT 25.00,
    completion_factor DECIMAL(5, 2) DEFAULT 18.00,
    incident_penalty DECIMAL(5, 2) DEFAULT 0.00,
    explanation TEXT,
    last_calculated_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_trust_driver FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 10. Bookings Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_reference VARCHAR(32) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    driver_id BIGINT NULL,
    start_date_time DATETIME NOT NULL,
    end_date_time DATETIME NOT NULL,
    pickup_location VARCHAR(255) NOT NULL,
    drop_location VARCHAR(255) NOT NULL,
    pickup_latitude DECIMAL(10, 7),
    pickup_longitude DECIMAL(10, 7),
    drop_latitude DECIMAL(10, 7),
    drop_longitude DECIMAL(10, 7),
    total_days INT NOT NULL DEFAULT 1,
    total_hours INT NULL,
    rental_amount DECIMAL(10, 2) NOT NULL,
    driver_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(10, 2) NOT NULL,
    booking_status ENUM('PENDING', 'CONFIRMED', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    payment_status ENUM('PENDING', 'PAID', 'REFUNDED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    pickup_time DATETIME NULL,
    return_time DATETIME NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bookings_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_bookings_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id),
    CONSTRAINT fk_bookings_driver FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE SET NULL,
    INDEX idx_bookings_status (booking_status),
    INDEX idx_bookings_ref (booking_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 11. Trips Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trips (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    start_latitude DECIMAL(10, 7) NOT NULL,
    start_longitude DECIMAL(10, 7) NOT NULL,
    dest_latitude DECIMAL(10, 7) NOT NULL,
    dest_longitude DECIMAL(10, 7) NOT NULL,
    current_latitude DECIMAL(10, 7),
    current_longitude DECIMAL(10, 7),
    distance_km DECIMAL(8, 2) DEFAULT 0.00,
    distance_traveled_km DECIMAL(8, 2) DEFAULT 0.00,
    status ENUM('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'NOT_STARTED',
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    simulation_step INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_trips_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 12. GPS & Telemetry Logs Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gps_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NULL,
    vehicle_id BIGINT NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    speed_kmh DECIMAL(6, 2) DEFAULT 0.00,
    heading_degrees DECIMAL(5, 2) NULL,
    battery_level_percent DECIMAL(5, 2) NULL,
    fuel_level_percent DECIMAL(5, 2) NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_gps_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_gps_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE,
    INDEX idx_gps_trip_time (trip_id, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 13. Payments Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    gateway ENUM('RAZORPAY', 'MOCK') NOT NULL DEFAULT 'MOCK',
    gateway_order_id VARCHAR(100),
    gateway_payment_id VARCHAR(100),
    gateway_signature VARCHAR(255),
    status ENUM('PENDING', 'PAID', 'REFUNDED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    INDEX idx_payments_order (gateway_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 14. Maintenance Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS maintenance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    service_type ENUM('ROUTINE', 'REPAIR', 'INSPECTION', 'BATTERY_HEALTH', 'EMERGENCY') NOT NULL,
    service_date DATE NOT NULL,
    next_service_date DATE NULL,
    description TEXT NOT NULL,
    cost DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    status ENUM('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'OVERDUE') NOT NULL DEFAULT 'SCHEDULED',
    odometer_km DECIMAL(10, 2) NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_maintenance_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE,
    INDEX idx_maintenance_vehicle (vehicle_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 15. Reviews Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    driver_id BIGINT NULL,
    rating DECIMAL(3, 2) NOT NULL,
    vehicle_rating DECIMAL(3, 2) NOT NULL,
    driver_rating DECIMAL(3, 2) NULL,
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_reviews_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id),
    CONSTRAINT fk_reviews_driver FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------------
-- 16. Notifications Table
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    message TEXT NOT NULL,
    type ENUM('BOOKING', 'PAYMENT', 'TRIP', 'VERIFICATION', 'MAINTENANCE', 'ALERT') NOT NULL DEFAULT 'ALERT',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    reference_type VARCHAR(50),
    reference_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notifications_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
