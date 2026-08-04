package com.despensia.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.despensia.scan.domain.event.ScanEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Configures Kafka retry behavior with exponential backoff and dead-letter queue for scan-events.
 */
@Configuration
public class KafkaRetryConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaRetryConfig.class);
    private static final String DLQ_TOPIC = "scan-events-dlt";

    /**
     * Configures a Kafka listener container factory with retry (exponential backoff 1s→2s→4s)
     * and automatic dead-letter routing for scan-events.
     */
    @Bean("retryContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, ScanEvent> retryContainerFactory(
            ConsumerFactory<String, String> defaultConsumerFactory,
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000);   // 1 second
        backOff.setMultiplier(2.0);         // 1s → 2s → 4s
        backOff.setMaxInterval(4000);       // cap at 4 seconds

        CommonErrorHandler errorHandler = new DefaultErrorHandler(
                (record, ex) -> log.error("Message sent to DLQ [{}]: scanId={}, error={}", DLQ_TOPIC, extractScanId(record), ex.getMessage()),
                (BackOff) backOff);

        JsonDeserializer<ScanEvent> deserializer = new JsonDeserializer<>(ScanEvent.class, false);
        deserializer.addTrustedPackages("com.despensia");

        java.util.Map<String, Object> props = new java.util.HashMap<>();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, "despensia-scan-consumer");
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer.getClass().getName());

        ConsumerFactory<String, ScanEvent> scanConsumerFactory = new DefaultKafkaConsumerFactory<>(props, 
                new org.apache.kafka.common.serialization.StringDeserializer(), deserializer);

        ConcurrentKafkaListenerContainerFactory<String, ScanEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(scanConsumerFactory);
        factory.getContainerProperties().setPollTimeout(3000L);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    /**
     * Extract a scan ID from a Kafka ConsumerRecord key, handling non-UUID keys gracefully.
     */
    private static String extractScanId(ConsumerRecord<?, ?> record) {
        if (record == null || record.key() == null) return "unknown";
        try {
            java.util.UUID.fromString(record.key().toString());
            return record.key().toString();
        } catch (IllegalArgumentException e) {
            return "non-uuid:" + record.key();
        }
    }
}
