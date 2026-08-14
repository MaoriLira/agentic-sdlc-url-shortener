package com.example.urlshortener.service;

import com.example.urlshortener.domain.event.ClickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes click events asynchronously (URL-202). Never blocks the redirect response:
 * KafkaTemplate#send returns immediately and the completion callback only logs failures.
 * Producer retries are handled by the Kafka client's own producer config (acks=all,
 * retries=3, application.yml); if the broker is unreachable beyond that, the event is
 * dropped and logged rather than buffered in-process (documented limitation).
 */
@Service
public class ClickEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClickEventPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;

    public ClickEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                @Value("${kafka.topics.click-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(ClickEvent event) {
        kafkaTemplate.send(topic, event.shortCode(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish click event for shortCode={}: {}",
                                event.shortCode(), ex.getMessage());
                    }
                });
    }
}
