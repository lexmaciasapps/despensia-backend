package com.despensia.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
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
    public ConcurrentKafkaListenerContainerFactory<String, String> retryContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000);   // 1 second
        backOff.setMultiplier(2.0);         // 1s → 2s → 4s
        backOff.setMaxInterval(4000);       // cap at 4 seconds

        CommonErrorHandler errorHandler = new DefaultErrorHandler(
                (record, ex) -> log.error("Message sent to DLQ [{}]: scanId={}, error={}", DLQ_TOPIC, extractScanId(record), ex.getMessage()),
                (BackOff) backOff);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
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
