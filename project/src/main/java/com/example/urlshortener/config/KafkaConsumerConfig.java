package com.example.urlshortener.config;

import com.example.urlshortener.domain.ClickEventDlq;
import com.example.urlshortener.domain.event.ClickEvent;
import com.example.urlshortener.repository.ClickEventDlqRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Bounded retry + dead-letter handling for the analytics consumer (URL-206). Failed
 * records are retried twice with a 1s pause; if still failing, the recoverer persists
 * the raw event to analytics.click_events_dlq instead of blocking the partition
 * indefinitely or silently dropping the event.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(ClickEventDlqRepository dlqRepository, ObjectMapper objectMapper) {
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            try {
                Object value = record.value();
                String shortCode = (value instanceof ClickEvent event) ? event.shortCode() : "unknown";
                String payload = objectMapper.writeValueAsString(value);
                dlqRepository.save(new ClickEventDlq(shortCode, payload, exception.getMessage()));
            } catch (Exception recoveryFailure) {
                log.error("Failed to persist click event to DLQ after retries exhausted", recoveryFailure);
            }
        }, new FixedBackOff(1000L, 2));
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory, DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
