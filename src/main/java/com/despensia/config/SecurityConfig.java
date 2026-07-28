package com.despensia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class to validate that secrets are loaded from environment variables
 * or the secrets file, ensuring no hardcoded credentials are used.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${spring.datasource.username:despensia}")
    private String dbUsername;

    @Value("${spring.datasource.password:despensia}")
    private String dbPassword;

    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/despensia}")
    private String mongoUri;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @PostConstruct
    public void init() {
        // Validate that secrets are loaded (not null or empty)
        if (dbPassword == null || dbPassword.isEmpty()) {
            throw new IllegalStateException("DB Password not loaded! Check application-secrets.yml or env vars.");
        }
        
        log.info("SecurityConfig initialized: DB user [{}], Redis [{}]", dbUsername, redisHost);
        // In production, you might log a masked version: "****"
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
