package com.drivesphere;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:prod_test_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "DRIVESPHERE_JWT_SECRET=super-secret-jwt-key-for-testing-in-production-profile-which-is-sufficiently-long-for-hs256"
})
public class ProductionProfileStartupTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Verify Spring Boot context starts successfully with active profile 'prod' and excludes SpringDoc")
    void contextLoadsInProductionProfile() {
        assertNotNull(context, "ApplicationContext must initialize successfully in prod profile");

        // Verify SpringDoc reflection autoconfigurations are excluded as intended in prod
        assertFalse(context.containsBean("openAPI"), "OpenAPI bean should not be registered in prod profile");
        assertFalse(context.containsBean("swaggerWelcome"), "Swagger UI welcome controller should not be registered in prod profile");
    }
}

