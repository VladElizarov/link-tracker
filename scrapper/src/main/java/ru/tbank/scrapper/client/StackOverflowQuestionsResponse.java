package ru.tbank.scrapper.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StackOverflowQuestionsResponse(List<Question> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Question(
        @JsonProperty("question_id") Long questionId,
        @JsonProperty("title") String title,
        @JsonProperty("last_activity_date") Long lastActivityDate
    ) {
    }
}
