package com.drivesphere;

import com.drivesphere.controller.HealthController;
import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.HealthResponse;
import com.drivesphere.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests validating DriveSphere foundational components.
 */
class DriveSphereApplicationTests {

    @Test
    void healthControllerShouldReturnHealthyStatus() {
        HealthController controller = new HealthController();
        ReflectionTestUtils.setField(controller, "applicationName", "drivesphere-backend");

        ResponseEntity<ApiResponse<HealthResponse>> response = controller.checkHealth();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        ApiResponse<HealthResponse> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());

        HealthResponse data = body.getData();
        assertNotNull(data);
        assertEquals("UP", data.getStatus());
        assertEquals("drivesphere-backend", data.getService());
    }

    @Test
    void jwtUtilsShouldGenerateAndValidateTokens() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000L);

        String username = "dispatcher@drivesphere.com";
        String token = jwtUtils.generateTokenFromUsername(username);

        assertNotNull(token);
        assertTrue(jwtUtils.validateJwtToken(token));
        assertEquals(username, jwtUtils.getUserNameFromJwtToken(token));
    }
}
