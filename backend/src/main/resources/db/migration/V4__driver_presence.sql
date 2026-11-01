-- ===================================================================
-- DriveSphere Flyway Migration V4: Driver Online Presence and Heartbeat
-- ===================================================================

ALTER TABLE drivers ADD COLUMN is_online BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE drivers ADD COLUMN last_seen_at TIMESTAMP NULL;

CREATE INDEX idx_drivers_presence ON drivers (is_online, is_available, last_seen_at);
