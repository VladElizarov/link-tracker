package ru.tbank.scrapper.model;

import java.util.List;

public record TrackedLink(long id, String url, List<String> tags, List<String> filters) {
}
