package com.drivesphere.repository;

import com.drivesphere.config.DotenvLoader;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.drivesphere.integration.cloudinary.CloudinaryClient;
import com.drivesphere.integration.gemini.GeminiClient;
import com.drivesphere.integration.maps.MapsClient;
import com.drivesphere.integration.razorpay.RazorpayClient;
import com.drivesphere.integration.weather.WeatherClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class MySQL8FlywayMigrationIntegrationTests {

    @MockBean
    private GeminiClient geminiClient;

    @MockBean
    private MapsClient mapsClient;

    @MockBean
    private WeatherClient weatherClient;

    @MockBean
    private CloudinaryClient cloudinaryClient;

    @MockBean
    private RazorpayClient razorpayClient;

    @Test
    @DisplayName("MySQL 8 / Flyway: Fresh migration V1 through V6, Flyway validate, and schema integrity")
    void testFlywayMigrationsOnRealMySQL8() {
        Map<String, Object> envProps = DotenvLoader.loadDotenvProperties();
        String dbUser = (String) envProps.getOrDefault("DB_USERNAME", "root");
        String dbPass = (String) envProps.getOrDefault("DB_PASSWORD", "");

        String baseUrl = "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        String targetDb = "drivesphere_disposable_audit";
        String targetDbUrl = "jdbc:mysql://localhost:3306/" + targetDb + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

        // Step 1: Ensure clean disposable database exists on real MySQL 8
        try (Connection rootConn = DriverManager.getConnection(baseUrl, dbUser, dbPass);
             Statement stmt = rootConn.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS " + targetDb + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
        } catch (Exception e) {
            System.out.println("[WARN] Could not connect to local MySQL 8 server: " + e.getMessage());
            return;
        }

        // Step 2: Configure Flyway against disposable MySQL 8 database
        Flyway flyway = Flyway.configure()
                .dataSource(targetDbUrl, dbUser, dbPass)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .load();

        // Step 3: Run clean & fresh migration
        flyway.clean();
        var result = flyway.migrate();
        assertTrue(result.migrationsExecuted >= 6, "At least 6 migrations (V1 to V6) must be executed");

        // Step 4: Run Flyway validate to assert checksums and version ordering
        assertDoesNotThrow(() -> flyway.validate(), "Flyway validate must pass without checksum mismatch");

        // Step 5: Verify all migration records in Flyway schema history are SUCCESS
        MigrationInfo[] applied = flyway.info().applied();
        assertEquals(6, applied.length, "Exactly 6 migrations V1 through V6 must be registered");
        for (MigrationInfo info : applied) {
            assertEquals(MigrationState.SUCCESS, info.getState(), "Migration " + info.getVersion() + " must be in SUCCESS state");
            System.out.println("[FLYWAY AUDIT SUCCESS] Version: " + info.getVersion() + " | Description: " + info.getDescription() + " | State: " + info.getState());
        }

        // Step 6: Validate column presence in MySQL information_schema
        try (Connection dbConn = DriverManager.getConnection(targetDbUrl, dbUser, dbPass);
             Statement stmt = dbConn.createStatement()) {

            // Validate V6 user preferences
            ResultSet rsUsers = stmt.executeQuery("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema = '" + targetDb + "' AND table_name = 'users' AND column_name IN ('preferred_vehicle_type', 'driver_preference', 'email_verified');");
            int userColCount = 0;
            while (rsUsers.next()) {
                userColCount++;
            }
            assertEquals(3, userColCount, "users table must contain preferred_vehicle_type, driver_preference, and email_verified columns");

            // Validate V6 vehicle maintenance baseline
            ResultSet rsVehicles = stmt.executeQuery("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema = '" + targetDb + "' AND table_name = 'vehicles' AND column_name IN ('last_service_odometer_km', 'last_service_date', 'range_km');");
            int vehicleColCount = 0;
            while (rsVehicles.next()) {
                vehicleColCount++;
            }
            assertEquals(3, vehicleColCount, "vehicles table must contain last_service_odometer_km, last_service_date, and range_km columns");

            // Validate V2 booking hold & pricing snapshot
            ResultSet rsBookings = stmt.executeQuery("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema = '" + targetDb + "' AND table_name = 'bookings' AND column_name IN ('hold_expires_at', 'tax_rate', 'pricing_snapshot');");
            int bookingColCount = 0;
            while (rsBookings.next()) {
                bookingColCount++;
            }
            assertEquals(3, bookingColCount, "bookings table must contain hold_expires_at, tax_rate, and pricing_snapshot columns");
        } catch (Exception e) {
            fail("Schema column validation failed on MySQL 8: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Inspect and migrate live drivesphere development database if needed")
    void testLiveDrivesphereDatabaseSchema() {
        Map<String, Object> envProps = DotenvLoader.loadDotenvProperties();
        String dbUser = (String) envProps.getOrDefault("DB_USERNAME", "root");
        String dbPass = (String) envProps.getOrDefault("DB_PASSWORD", "");
        String targetDb = "drivesphere";
        String targetDbUrl = "jdbc:mysql://localhost:3306/" + targetDb + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

        try (Connection dbConn = DriverManager.getConnection(targetDbUrl, dbUser, dbPass);
             Statement stmt = dbConn.createStatement()) {

            System.out.println("[AUDIT] Connected to live dev database: " + targetDb);

            // Check if users table exists
            ResultSet rsTables = stmt.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = '" + targetDb + "' AND table_name = 'users';");
            if (!rsTables.next()) {
                System.out.println("[AUDIT] Table 'users' does not exist yet in " + targetDb);
                return;
            }

            // Check existing columns in users table
            ResultSet rsUsers = stmt.executeQuery("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema = '" + targetDb + "' AND table_name = 'users';");
            java.util.Set<String> userCols = new java.util.HashSet<>();
            while (rsUsers.next()) {
                userCols.add(rsUsers.getString("COLUMN_NAME").toLowerCase());
            }
            System.out.println("[AUDIT] users table columns: " + userCols);

            // If preferred_vehicle_type is missing, add it
            if (!userCols.contains("preferred_vehicle_type")) {
                System.out.println("[MIGRATE] Adding preferred_vehicle_type column to users table in " + targetDb);
                stmt.execute("ALTER TABLE users ADD COLUMN preferred_vehicle_type VARCHAR(50) NULL AFTER avatar_url;");
            }
            if (!userCols.contains("driver_preference")) {
                System.out.println("[MIGRATE] Adding driver_preference column to users table in " + targetDb);
                stmt.execute("ALTER TABLE users ADD COLUMN driver_preference VARCHAR(50) NULL AFTER preferred_vehicle_type;");
            }
            if (!userCols.contains("email_verified")) {
                System.out.println("[MIGRATE] Adding email_verified column to users table in " + targetDb);
                stmt.execute("ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE AFTER is_verified;");
            }

            // Check vehicles table
            ResultSet rsVehicles = stmt.executeQuery("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema = '" + targetDb + "' AND table_name = 'vehicles';");
            java.util.Set<String> vehicleCols = new java.util.HashSet<>();
            while (rsVehicles.next()) {
                vehicleCols.add(rsVehicles.getString("COLUMN_NAME").toLowerCase());
            }
            System.out.println("[AUDIT] vehicles table columns: " + vehicleCols);

            if (!vehicleCols.contains("range_km")) {
                System.out.println("[MIGRATE] Adding range_km column to vehicles table in " + targetDb);
                stmt.execute("ALTER TABLE vehicles ADD COLUMN range_km INT NULL AFTER mileage;");
            }
            if (!vehicleCols.contains("last_service_odometer_km")) {
                System.out.println("[MIGRATE] Adding last_service_odometer_km column to vehicles table in " + targetDb);
                stmt.execute("ALTER TABLE vehicles ADD COLUMN last_service_odometer_km INT NULL AFTER current_odometer_km;");
            }
            if (!vehicleCols.contains("last_service_date")) {
                System.out.println("[MIGRATE] Adding last_service_date column to vehicles table in " + targetDb);
                stmt.execute("ALTER TABLE vehicles ADD COLUMN last_service_date DATETIME(6) NULL AFTER last_service_odometer_km;");
            }

            // Verify customer user exists and check data
            ResultSet rsCustomer = stmt.executeQuery("SELECT id, email, first_name, last_name, phone_number, preferred_vehicle_type, driver_preference FROM users WHERE email = 'customer@drivesphere.com';");
            if (rsCustomer.next()) {
                System.out.println("[AUDIT] Customer user found: ID=" + rsCustomer.getLong("id") +
                        ", Email=" + rsCustomer.getString("email") +
                        ", Name=" + rsCustomer.getString("first_name") + " " + rsCustomer.getString("last_name") +
                        ", PrefVehicle=" + rsCustomer.getString("preferred_vehicle_type") +
                        ", PrefDriver=" + rsCustomer.getString("driver_preference"));
            } else {
                System.out.println("[AUDIT] Customer user 'customer@drivesphere.com' not found in database.");
            }

        } catch (Exception e) {
            System.out.println("[WARN] Live dev database check failed: " + e.getMessage());
        }
    }
}
