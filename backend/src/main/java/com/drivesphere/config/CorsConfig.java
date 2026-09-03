package com.drivesphere.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Cross-Origin Resource Sharing (CORS) Configuration.
 * Configures origin permissions for local and remote frontend clients.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${drivesphere.cors.allowed-origins:http://localhost:5500,http://127.0.0.1:5500,http://localhost:3000,http://localhost:5173,http://localhost:8080}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        String originsConfig = allowedOrigins != null ? allowedOrigins : "";
        String[] origins = Arrays.stream(originsConfig.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    @NonNull
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        String originsConfig = allowedOrigins != null ? allowedOrigins : "";
        List<String> origins = Arrays.stream(originsConfig.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
