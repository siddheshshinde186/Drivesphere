package com.drivesphere.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class HealthEndpointSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/health is publicly accessible for container and platform uptime checks")
    void publicHealthEndpointIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/health")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    @DisplayName("GET /api/health/diagnostics returns 401 Unauthorized when unauthenticated")
    void diagnosticsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/health/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/health/diagnostics returns 403 Forbidden for non-admin users (CUSTOMER)")
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void diagnosticsForbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/health/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/health/diagnostics returns 200 OK with JVM telemetry for ADMIN users")
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void diagnosticsAccessibleForAdmin() throws Exception {
        mockMvc.perform(get("/api/health/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loadedClassCount", greaterThan(0)))
                .andExpect(jsonPath("$.data.metaspaceUsedMb", notNullValue()))
                .andExpect(jsonPath("$.data.heapUsedMb", notNullValue()));
    }
}
