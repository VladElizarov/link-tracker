package ru.tbank.scrapper.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import ru.tbank.scrapper.client.GitHubClient;
import ru.tbank.scrapper.client.GitHubIssueResponse;
import ru.tbank.scrapper.client.StackOverflowClient;
import ru.tbank.scrapper.client.StackOverflowPostsResponse;
import ru.tbank.scrapper.client.StackOverflowQuestionsResponse;
import ru.tbank.scrapper.config.ScrapperConfig;
import ru.tbank.scrapper.exception.LinkCheckException;
import ru.tbank.scrapper.link.GitHubLink;
import ru.tbank.scrapper.link.LinkParser;
import ru.tbank.scrapper.link.ParsedLink;
import ru.tbank.scrapper.link.StackOverflowLink;
import ru.tbank.scrapper.model.ContentUpdate;
import ru.tbank.scrapper.model.UpdateType;

@Service
public class LinkUpdateChecker {

    private static final String UNKNOWN_AUTHOR = "неизвестный пользователь";

    private final LinkParser linkParser;
    private final GitHubClient gitHubClient;
    private final StackOverflowClient stackOverflowClient;
    private final ScrapperConfig config;

    public LinkUpdateChecker(
        LinkParser linkParser,
        GitHubClient gitHubClient,
        StackOverflowClient stackOverflowClient,
        ScrapperConfig config
    ) {
        this.linkParser = linkParser;
        this.gitHubClient = gitHubClient;
        this.stackOverflowClient = stackOverflowClient;
        this.config = config;
    }

    public List<ContentUpdate> getUpdates(String url, Instant since) {
        ParsedLink parsed = linkParser.parse(url);
        if (parsed == null) {
            throw new LinkCheckException("ссылка не поддерживается");
        }

        List<ContentUpdate> updates;
        try {
            if (parsed instanceof GitHubLink gitHubLink) {
                updates = getGitHubUpdates(gitHubLink, since);
            } else {
                updates = getStackOverflowUpdates((StackOverflowLink) parsed, since);
            }
        } catch (LinkCheckException e) {
            throw e;
        } catch (Exception e) {
            throw new LinkCheckException("внешний API ответил ошибкой: " + e.getMessage(), e);
        }

        updates.sort(Comparator.comparing(ContentUpdate::createdAt));
        return updates;
    }

    private List<ContentUpdate> getGitHubUpdates(GitHubLink link, Instant since) {
        List<GitHubIssueResponse> issues = gitHubClient.getIssues(link.owner(), link.repo());
        if (issues == null) {
            throw new LinkCheckException("GitHub прислал пустой ответ");
        }

        List<ContentUpdate> result = new ArrayList<>();
        for (GitHubIssueResponse issue : issues) {
            if (issue == null || !isNew(issue.createdAt(), since)) {
                continue;
            }
            UpdateType type = issue.pullRequest() != null ? UpdateType.PULL_REQUEST : UpdateType.ISSUE;
            String author = issue.user() != null ? issue.user().login() : null;
            result.add(new ContentUpdate(type, issue.title(), orUnknown(author), issue.createdAt(), issue.body()));
        }
        return result;
    }

    private List<ContentUpdate> getStackOverflowUpdates(StackOverflowLink link, Instant since) {
        String key = blankToNull(config.stackOverflow().key());
        List<StackOverflowPostsResponse.Post> answers =
            getItems(stackOverflowClient.getAnswers(link.questionId(), key));
        List<StackOverflowPostsResponse.Post> comments =
            getItems(stackOverflowClient.getComments(link.questionId(), key));

        List<StackOverflowPostsResponse.Post> newAnswers = filterNew(answers, since);
        List<StackOverflowPostsResponse.Post> newComments = filterNew(comments, since);
        if (newAnswers.isEmpty() && newComments.isEmpty()) {
            return new ArrayList<>();
        }

        String title = getQuestionTitle(link.questionId(), key);
        List<ContentUpdate> result = new ArrayList<>();
        for (StackOverflowPostsResponse.Post answer : newAnswers) {
            result.add(toContentUpdate(UpdateType.ANSWER, title, answer));
        }
        for (StackOverflowPostsResponse.Post comment : newComments) {
            result.add(toContentUpdate(UpdateType.COMMENT, title, comment));
        }
        return result;
    }

    private List<StackOverflowPostsResponse.Post> getItems(StackOverflowPostsResponse response) {
        if (response == null || response.items() == null) {
            throw new LinkCheckException("StackOverflow прислал ответ без поля items");
        }
        return response.items();
    }

    private List<StackOverflowPostsResponse.Post> filterNew(List<StackOverflowPostsResponse.Post> posts, Instant since) {
        List<StackOverflowPostsResponse.Post> result = new ArrayList<>();
        for (StackOverflowPostsResponse.Post post : posts) {
            if (post != null && post.creationDate() != null
                && isNew(Instant.ofEpochSecond(post.creationDate()), since)) {
                result.add(post);
            }
        }
        return result;
    }

    private String getQuestionTitle(long questionId, String key) {
        StackOverflowQuestionsResponse response = stackOverflowClient.getQuestion(questionId, key);
        if (response == null || response.items() == null || response.items().isEmpty()) {
            throw new LinkCheckException("StackOverflow не вернул вопрос " + questionId);
        }
        return htmlToText(response.items().get(0).title());
    }

    private ContentUpdate toContentUpdate(UpdateType type, String title, StackOverflowPostsResponse.Post post) {
        String author = post.owner() != null ? htmlToText(post.owner().displayName()) : null;
        return new ContentUpdate(
            type,
            title,
            orUnknown(author),
            Instant.ofEpochSecond(post.creationDate()),
            htmlToText(post.body())
        );
    }

    private boolean isNew(Instant createdAt, Instant since) {
        return createdAt != null && since != null && createdAt.isAfter(since);
    }

    static String htmlToText(String html) {
        if (html == null) {
            return null;
        }
        String withoutTags = html.replaceAll("<[^>]*>", " ");
        return HtmlUtils.htmlUnescape(withoutTags).replaceAll("\\s+", " ").trim();
    }

    private String orUnknown(String author) {
        if (author == null || author.isBlank()) {
            return UNKNOWN_AUTHOR;
        }
        return author;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
