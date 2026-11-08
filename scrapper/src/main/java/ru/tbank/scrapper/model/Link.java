package ru.tbank.scrapper.model;

import java.time.Instant;

public record Link(long id, String url, Instant lastUpdate) {
}
