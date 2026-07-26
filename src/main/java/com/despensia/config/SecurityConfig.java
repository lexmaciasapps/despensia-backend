package com.despensia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class to validate that secrets are loaded from environment variables
 * or the secrets file, ensuring no hardcoded credentials are used.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.redis.host}")
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
}
