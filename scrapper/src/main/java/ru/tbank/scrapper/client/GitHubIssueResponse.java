package ru.tbank.scrapper.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueResponse(
    @JsonProperty("title") String title,
    @JsonProperty("user") User user,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("body") String body,
    @JsonProperty("pull_request") Map<String, Object> pullRequest
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(@JsonProperty("login") String login) {
    }
}
