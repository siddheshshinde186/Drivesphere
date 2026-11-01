-- ===================================================================
-- DriveSphere Flyway Migration V2: Security, Holds, Telemetry & Refunds
-- ===================================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_email VARCHAR(120),
    actor_role VARCHAR(50),
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(60) NOT NULL,
    resource_id VARCHAR(60),
    details TEXT,
    ip_address VARCHAR(60),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_audit_actor (actor_email),
    INDEX idx_audit_action (action),
    INDEX idx_audit_resource (resource_type, resource_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(128) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_refresh_token (token),
    INDEX idx_refresh_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Enhance Bookings with Concurrency Hold and Pricing Snapshot
ALTER TABLE bookings 
    ADD COLUMN hold_expires_at DATETIME NULL,
    ADD COLUMN tax_rate DECIMAL(5, 4) DEFAULT 0.1800,
    ADD COLUMN discount_amount DECIMAL(10, 2) DEFAULT 0.00,
    ADD COLUMN deposit_amount DECIMAL(10, 2) DEFAULT 0.00,
    ADD COLUMN pricing_snapshot TEXT NULL;

-- Enhance Vehicles with Range and EV Consumption
ALTER TABLE vehicles
    ADD COLUMN range_km INT NULL,
    ADD COLUMN energy_consumption_kwh_per_100km DECIMAL(5, 2) NULL;

-- Enhance Payments with Gateway Refund Reconciliation & Webhook Idempotency
ALTER TABLE payments
    ADD COLUMN refund_id VARCHAR(100) NULL,
    ADD COLUMN refund_amount DECIMAL(10, 2) NULL,
    ADD COLUMN refund_status VARCHAR(40) NULL,
    ADD COLUMN idempotency_key VARCHAR(100) NULL,
    ADD COLUMN raw_webhook_payload TEXT NULL;
