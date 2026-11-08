package ru.tbank.scrapper.model;

import java.time.Instant;

public record ContentUpdate(UpdateType type, String title, String author, Instant createdAt, String text) {
}
