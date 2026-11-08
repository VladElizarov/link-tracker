package ru.tbank.scrapper.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaTopicConfig(
    @NotBlank String topic,
    @Positive int partitions,
    @Positive short replicas,
    @Positive int minInsyncReplicas,
    Duration sendTimeout
) {
}
