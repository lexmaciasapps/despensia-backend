package com.despensia.config;

import com.despensia.scan.domain.event.ScanEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka configuration — topic auto-creation and scan-event producer.
 *
 * <p>All topics are created automatically by Spring at startup via {@link NewTopic} beans.
 * No manual docker-compose or CLI setup is required for topic provisioning.</p>
 */
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

    /** Scan-events topic — receives scan events published by ProcessImageUseCase. */
    @Bean
    public NewTopic scanTopic() {
        return new NewTopic("scan-events", 1, (short) 1);
    }

    /** Dead-letter queue for permanently failed scan events. Created automatically at startup. */
    @Bean
    public NewTopic scanDltTopic() {
        return new NewTopic("scan-events-dlt", 1, (short) 1);
    }

    /**
     * JSON-capable producer factory for {@link ScanEvent} serialization.
     * Inherits bootstrap servers from Spring Boot auto-config via application.yml properties.
     */
    @Bean("scanProducerFactory")
    public ProducerFactory<String, ScanEvent> scanProducerFactory(
            Environment env) {
        String bootstrapServers = env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092");

        java.util.Map<String, Object> props = new java.util.HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean("scanKafkaTemplate")
    public KafkaTemplate<String, ScanEvent> scanKafkaTemplate(
            ProducerFactory<String, ScanEvent> scanProducerFactory) {
        return new KafkaTemplate<>(scanProducerFactory);
    }
}
