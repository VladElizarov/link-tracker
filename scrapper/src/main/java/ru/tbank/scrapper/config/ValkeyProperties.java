package ru.tbank.scrapper.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.valkey")
public record ValkeyProperties(String sentinelMaster, List<String> sentinelNodes, String url, Duration timeout) {
}
