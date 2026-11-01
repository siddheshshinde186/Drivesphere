package com.drivesphere.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Spring Boot EnvironmentPostProcessor that automatically loads the root .env file
 * into the Spring Environment before beans and auto-configurations are initialized.
 * Enables automatic discovery whether launched from project root, backend folder, or IDE.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> dotenvProps = DotenvLoader.loadDotenvProperties();
        if (!dotenvProps.isEmpty()) {
            // Added with lowest priority among property sources to ensure
            // OS environment variables and system properties always override .env values
            environment.getPropertySources().addLast(
                    new MapPropertySource(DotenvLoader.DOTENV_PROPERTY_SOURCE_NAME, dotenvProps)
            );
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
