package ru.tbank.scrapper.link;

public record GitHubLink(String owner, String repo) implements ParsedLink {
}
