package com.despensia.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic productTopic() {
        return new NewTopic("product-events", 1, (short) 1);
    }

    @Bean
    public NewTopic inventoryTopic() {
        return new NewTopic("inventory-events", 1, (short) 1);
    }

    @Bean
    public NewTopic scanTopic() {
        return new NewTopic("scan-events", 1, (short) 1);
    }
}
