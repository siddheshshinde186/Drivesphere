package com.drivesphere.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security 6 / Boot 3.x Security Filter Chain Configuration with null-safety.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthEntryPointJwt unauthorizedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(@NonNull AuthEntryPointJwt unauthorizedHandler,
                          @NonNull JwtAuthenticationFilter jwtAuthenticationFilter,
                          @NonNull CorrelationIdFilter correlationIdFilter,
                          @NonNull CorsConfigurationSource corsConfigurationSource) {
        this.unauthorizedHandler = unauthorizedHandler;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.correlationIdFilter = correlationIdFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    @NonNull
    public SecurityFilterChain securityFilterChain(@NonNull HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Frontend static assets & portals (excluding admin portal)
                        .requestMatchers(
                                "/", "/index.html", "/*.html",
                                "/assets/**", "/customer/**", "/driver/**", "/fleet-manager/**",
                                "/error", "/favicon.ico",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
                        ).permitAll()
                        // Server-side Admin Portal & Admin API Security Guard
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        // Public Actuator health vs protected internal metrics
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Public APIs
                        .requestMatchers("/api/health/**", "/api/documents/public/**").permitAll()
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/verify-email",
                                "/api/auth/send-verification",
                                "/api/auth/resend-verification",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        ).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/vehicles/available",
                                "/api/vehicles/fleet",
                                "/api/vehicles/explore",
                                "/api/vehicles/available-dates",
                                "/api/vehicles/filter",
                                "/api/vehicles/{id:[0-9]+}",
                                "/api/drivers/available",
                                "/api/reviews/**"
                        ).permitAll()
                        .requestMatchers("/api/maps/**", "/api/weather/**", "/api/ai/**").permitAll()
                        .requestMatchers("/api/payments/webhook").permitAll()
                        .anyRequest().authenticated()
                );

        http.addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @NonNull
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @NonNull
    public AuthenticationManager authenticationManager(@NonNull AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
