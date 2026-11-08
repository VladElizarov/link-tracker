package ru.tbank.scrapper.client;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(accept = "application/vnd.github+json")
public interface GitHubClient {

    @GetExchange("/repos/{owner}/{repo}/issues?state=all&sort=created&direction=desc&per_page=30")
    List<GitHubIssueResponse> getIssues(@PathVariable String owner, @PathVariable String repo);
}
