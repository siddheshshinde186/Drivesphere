package com.drivesphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DriveSphere – Smart Mobility & Fleet Intelligence Platform
 * Main Spring Boot Application Entry Point.
 */
@SpringBootApplication
@EnableScheduling
public class DriveSphereApplication {

    public static void main(String[] args) {
        // Pre-populate system properties from discovered .env file if not already set by OS environment
        java.util.Map<String, Object> props = com.drivesphere.config.DotenvLoader.loadDotenvProperties();
        props.forEach((k, v) -> {
            if (System.getProperty(k) == null && System.getenv(k) == null && v != null) {
                System.setProperty(k, v.toString());
            }
        });
        SpringApplication.run(DriveSphereApplication.class, args);
    }
}
