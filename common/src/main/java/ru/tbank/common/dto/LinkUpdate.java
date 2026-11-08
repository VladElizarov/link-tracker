package ru.tbank.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record LinkUpdate(
    @NotNull Long id,
    @NotBlank String url,
    String description,
    @NotEmpty List<@NotNull Long> tgChatIds
) {
}
