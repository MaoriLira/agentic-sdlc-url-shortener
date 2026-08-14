package com.example.urlshortener.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topics.click-events}")
    private String clickEventsTopicName;

    @Bean
    public NewTopic clickEventsTopic() {
        return TopicBuilder.name(clickEventsTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
