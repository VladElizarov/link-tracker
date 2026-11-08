package ru.tbank.scrapper.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(prefix = "app", name = "message-transport", havingValue = "KAFKA", matchIfMissing = true)
public class KafkaConfig {

    @Bean
    public NewTopic linkUpdatesTopic(KafkaTopicConfig config) {
        return TopicBuilder.name(config.topic())
            .partitions(config.partitions())
            .replicas(config.replicas())
            .config("min.insync.replicas", String.valueOf(config.minInsyncReplicas()))
            .build();
    }
}
