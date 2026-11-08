package ru.tbank.scrapper.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface StackOverflowClient {

    @GetExchange("/2.3/questions/{id}?site=stackoverflow")
    StackOverflowQuestionsResponse getQuestion(
        @PathVariable long id,
        @RequestParam(name = "key", required = false) String key
    );

    @GetExchange("/2.3/questions/{id}/answers?site=stackoverflow&sort=creation&order=desc&filter=withbody")
    StackOverflowPostsResponse getAnswers(
        @PathVariable long id,
        @RequestParam(name = "key", required = false) String key
    );

    @GetExchange("/2.3/questions/{id}/comments?site=stackoverflow&sort=creation&order=desc&filter=withbody")
    StackOverflowPostsResponse getComments(
        @PathVariable long id,
        @RequestParam(name = "key", required = false) String key
    );
}
