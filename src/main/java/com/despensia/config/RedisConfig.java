package com.despensia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Configuration for Spring Data Redis — provides a {@link StringRedisTemplate} bean.
 */
@Configuration
public class RedisConfig {

    /**
     * Create a String-based Redis template using the auto-configured connection factory.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
