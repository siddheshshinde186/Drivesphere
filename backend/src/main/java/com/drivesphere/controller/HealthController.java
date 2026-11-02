package com.drivesphere.controller;

import com.drivesphere.diagnostics.JvmDiagnosticsService;
import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.HealthResponse;
import com.drivesphere.dto.JvmDiagnosticsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Health check REST controller providing platform operational status
 * and JVM memory/metaspace diagnostics.
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

    @Autowired(required = false)
    private JvmDiagnosticsService jvmDiagnosticsService;

    public HealthController() {
    }

    public HealthController(JvmDiagnosticsService jvmDiagnosticsService) {
        this.jvmDiagnosticsService = jvmDiagnosticsService;
    }

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

    @GetMapping("/diagnostics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<JvmDiagnosticsDto>> getDiagnostics() {
        if (jvmDiagnosticsService == null) {
            jvmDiagnosticsService = new JvmDiagnosticsService();
        }
        return ResponseEntity.ok(ApiResponse.ok("Live JVM memory and Metaspace diagnostics", jvmDiagnosticsService.captureDiagnostics()));
    }
}
