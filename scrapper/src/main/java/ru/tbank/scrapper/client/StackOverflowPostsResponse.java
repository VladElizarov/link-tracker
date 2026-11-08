package ru.tbank.scrapper.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StackOverflowPostsResponse(List<Post> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Post(
        @JsonProperty("owner") Owner owner,
        @JsonProperty("creation_date") Long creationDate,
        @JsonProperty("body") String body
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(@JsonProperty("display_name") String displayName) {
    }
}
