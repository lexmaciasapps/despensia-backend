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
}
