package ru.tbank.bot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record BotConfig(@NotBlank String telegramToken, @NotBlank String scrapperUrl) {
}
