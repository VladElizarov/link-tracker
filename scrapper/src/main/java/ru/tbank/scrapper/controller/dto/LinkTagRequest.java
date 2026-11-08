package ru.tbank.scrapper.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkTagRequest(@NotBlank String link, @NotBlank String tag) {
}
