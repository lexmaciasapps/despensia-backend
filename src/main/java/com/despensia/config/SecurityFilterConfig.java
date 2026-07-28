package com.despensia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration to permit access to actuator endpoints for health checks
 * while keeping the rest of the application secured by Spring Security.
 */
@Configuration
@EnableWebSecurity
public class SecurityFilterConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/public/**").permitAll() // For future public endpoints
                .anyRequest().authenticated()
            );
        return http.build();
    }

    /**
     * TODO: [Security] Implement JWT authentication when moving to production.
     * Current state: Spring Security's auth gate is a placeholder — any request passes as "anonymous".
     * The login endpoint returns token=null (see UsersController.login()).
     * 
     * Required before prod:
     * 1. Add spring-boot-starter-oauth2-resource-server or jjwt library
     * 2. Create JwtAuthenticationFilter that intercepts /api/* requests and validates Bearer tokens
     * 3. Update SecurityFilterChain to permit all "/api/auth/**" (register/login) but require JWT for the rest
     * 4. Add token expiration, refresh token rotation, and key management
     * 5. Implement rate limiting on login/register endpoints (e.g., Bucket4j or Resilience4j)
     */
}
