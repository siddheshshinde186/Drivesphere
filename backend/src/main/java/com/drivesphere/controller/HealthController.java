package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Health check REST controller providing platform operational status.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Value("${spring.application.name:drivesphere-backend}")
    private String applicationName;

    @Value("${spring.profiles.active:development}")
    private String activeProfile;

    @Value("${drivesphere.app.version:1.0.0}")
    private String appVersion;

    @GetMapping
    public ResponseEntity<ApiResponse<HealthResponse>> checkHealth() {
        HealthResponse health = HealthResponse.builder()
                .status("UP")
                .service(applicationName)
                .version(appVersion)
                .environment(activeProfile)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.ok(ApiResponse.ok("DriveSphere API is operational and healthy", health));
    }
}
