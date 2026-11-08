package ru.tbank.scrapper.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record ScrapperConfig(
    @NotBlank String botUrl,
    @NotNull @Valid GitHub github,
    @NotNull @Valid StackOverflow stackOverflow,
    @NotNull @Valid Scheduler scheduler,
    @NotNull @Valid Database database
) {

    public record GitHub(@NotBlank String url, String token) {
    }

    public record StackOverflow(@NotBlank String url, String key) {
    }

    public record Scheduler(
        boolean enable,
        @NotNull Duration interval,
        @Min(50) @Max(500) int batchSize,
        @Positive int threads
    ) {
    }

    public record Database(@NotNull AccessType accessType) {
    }

    public enum AccessType {
        SQL,
        ORM
    }
}
