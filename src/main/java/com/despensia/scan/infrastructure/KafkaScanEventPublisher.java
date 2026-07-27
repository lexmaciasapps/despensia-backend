package com.despensia.scan.infrastructure;

import com.despensia.scan.domain.event.ScanEvent;
import com.despensia.scan.service.ScanEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service("kafkaScanEventPublisher")
public class KafkaScanEventPublisher implements ScanEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaScanEventPublisher.class);
    private static final String TOPIC = "scan-events";

    private final KafkaTemplate<String, ScanEvent> kafkaTemplate;

    public KafkaScanEventPublisher(@org.springframework.beans.factory.annotation.Qualifier("scanKafkaTemplate") KafkaTemplate<String, ScanEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(ScanEvent event) {
        log.info("Publishing scan event: scanId={}, type={}", event.scanId(), event.scanType());

        CompletableFuture<SendResult<String, ScanEvent>> future =
                kafkaTemplate.send(TOPIC, event.scanId().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Scan event published successfully: scanId={}, partition={}, offset={}",
                        event.scanId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish scan event: scanId={}", event.scanId(), ex);
            }
        });
    }
}
