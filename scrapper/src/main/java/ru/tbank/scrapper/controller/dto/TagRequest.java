package ru.tbank.scrapper.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record TagRequest(@NotBlank String name) {
}
