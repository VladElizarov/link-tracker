package ru.tbank.scrapper.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cache")
public record CacheConfig(@NotNull Mode mode, @NotNull Duration ttl) {

    public enum Mode {
        NONE,
        VALKEY,
        CLIENT_SIDE
    }
}
