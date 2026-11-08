package ru.tbank.common.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AddLinkRequest(
    @NotBlank String link,
    List<String> tags,
    List<String> filters
) {
}
