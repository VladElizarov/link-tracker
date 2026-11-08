package ru.tbank.bot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record BotKafkaProperties(
    @NotBlank String topic,
    @NotBlank String dlqTopic,
    @Positive int partitions,
    @Positive short replicas,
    @Positive int minInsyncReplicas,
    @NotNull @Valid Retry retry
) {

    public record Retry(@Positive int maxAttempts, @NotNull Duration backoff) {
    }
}
