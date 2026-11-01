package com.drivesphere.config;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Robust .env file loader for DriveSphere.
 * Automatically discovers .env from working directory or parent directories
 * when launched from root, backend, or IDE runners.
 * Respects configuration precedence: OS environment variables and JVM system properties
 * always override .env file entries.
 */
@Slf4j
public class DotenvLoader {

    public static final String DOTENV_PROPERTY_SOURCE_NAME = "drivesphereDotenvProperties";

    /**
     * Discovers and parses the .env file if present.
     * Returns an unmodifiable map of parsed properties with Spring-friendly alias mappings.
     */
    public static Map<String, Object> loadDotenvProperties() {
        File envFile = findDotenvFile();
        if (envFile == null || !envFile.exists() || !envFile.isFile()) {
            log.debug("No .env file discovered in standard search paths. Relying on system environment variables.");
            return Collections.emptyMap();
        }

        Map<String, Object> props = new LinkedHashMap<>();
        log.info("Discovered DriveSphere environment configuration file at: {}", envFile.getAbsolutePath());

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }

                int eqIndex = line.indexOf('=');
                if (eqIndex <= 0) {
                    continue;
                }

                String key = line.substring(0, eqIndex).trim();
                String rawValue = line.substring(eqIndex + 1).trim();

                // Strip outer quotes if present
                String value = stripQuotes(rawValue);

                // OS environment variable takes precedence
                String existingEnv = System.getenv(key);
                if (existingEnv != null && !existingEnv.isBlank()) {
                    props.put(key, existingEnv);
                    continue;
                }

                // JVM system property takes next precedence
                String existingSysProp = System.getProperty(key);
                if (existingSysProp != null && !existingSysProp.isBlank()) {
                    props.put(key, existingSysProp);
                    continue;
                }

                props.put(key, value);

                // Add standard Spring Boot configuration property aliases
                populateSpringAliases(key, value, props);
            }
        } catch (IOException e) {
            log.warn("Failed reading .env file from {}: {}", envFile.getAbsolutePath(), e.getMessage());
        }

        return Collections.unmodifiableMap(props);
    }

    /**
     * Discovers .env file in candidate locations.
     */
    public static File findDotenvFile() {
        // 1. Explicit override via system property or env var
        String explicitPath = System.getProperty("ENV_FILE", System.getenv("ENV_FILE"));
        if (explicitPath != null && !explicitPath.isBlank()) {
            File f = new File(explicitPath);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }

        // 2. Search working directory and upward
        String userDir = System.getProperty("user.dir", ".");
        File currentDir = new File(userDir);

        String[] searchPaths = new String[]{
                ".env",
                "../.env",
                "../../.env",
                "drivesphere/.env"
        };

        for (String relative : searchPaths) {
            File candidate = new File(currentDir, relative);
            try {
                if (candidate.exists() && candidate.isFile()) {
                    return candidate.getCanonicalFile();
                }
            } catch (IOException ignored) {
            }
        }

        return null;
    }

    private static String stripQuotes(String raw) {
        if (raw == null || raw.length() < 2) {
            return raw;
        }
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static void populateSpringAliases(String key, String value, Map<String, Object> props) {
        switch (key) {
            case "SMTP_HOST" -> props.putIfAbsent("spring.mail.host", value);
            case "SMTP_PORT" -> props.putIfAbsent("spring.mail.port", value);
            case "SMTP_USERNAME" -> props.putIfAbsent("spring.mail.username", value);
            case "SMTP_PASSWORD" -> props.putIfAbsent("spring.mail.password", value);
            case "SMTP_AUTH" -> props.putIfAbsent("spring.mail.properties.mail.smtp.auth", value);
            case "SMTP_STARTTLS" -> props.putIfAbsent("spring.mail.properties.mail.smtp.starttls.enable", value);
            case "SMTP_FROM" -> props.putIfAbsent("drivesphere.mail.from", value);
            case "FRONTEND_URL" -> props.putIfAbsent("drivesphere.mail.frontend-url", value);
            case "DB_URL" -> props.putIfAbsent("spring.datasource.url", value);
            case "DB_USERNAME" -> props.putIfAbsent("spring.datasource.username", value);
            case "DB_PASSWORD" -> props.putIfAbsent("spring.datasource.password", value);
            case "SERVER_PORT" -> props.putIfAbsent("server.port", value);
            case "JWT_SECRET" -> props.putIfAbsent("drivesphere.jwt.secret", value);
            case "DRIVESPHERE_JWT_SECRET" -> props.putIfAbsent("drivesphere.jwt.secret", value);
            case "GEMINI_API_KEY" -> props.putIfAbsent("drivesphere.gemini.api-key", value);
            case "GEMINI_MODEL" -> props.putIfAbsent("drivesphere.gemini.model", value);
            case "ORS_API_KEY" -> props.putIfAbsent("drivesphere.openrouteservice.api-key", value);
            default -> {}
        }
    }
}
