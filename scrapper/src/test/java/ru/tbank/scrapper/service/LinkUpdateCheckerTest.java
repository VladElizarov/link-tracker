package ru.tbank.scrapper.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import ru.tbank.scrapper.config.ClientConfig;
import ru.tbank.scrapper.config.ScrapperConfig;
import ru.tbank.scrapper.exception.LinkCheckException;
import ru.tbank.scrapper.link.LinkParser;
import ru.tbank.scrapper.model.ContentUpdate;
import ru.tbank.scrapper.model.UpdateType;

class LinkUpdateCheckerTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(wireMockConfig().dynamicPort());

    private static final String GITHUB_LINK = "https://github.com/user/repo";
    private static final String GITHUB_ISSUES = "/repos/user/repo/issues";
    private static final String SO_LINK = "https://stackoverflow.com/questions/123";

    private static final Instant SINCE = Instant.parse("2026-11-01T00:00:00Z");

    private static LinkUpdateChecker checker;

    @BeforeAll
    static void setUp() {
        WIRE_MOCK.start();
        ScrapperConfig config = new ScrapperConfig(
            "http://bot",
            new ScrapperConfig.GitHub(WIRE_MOCK.baseUrl(), "test-github-token"),
            new ScrapperConfig.StackOverflow(WIRE_MOCK.baseUrl(), null),
            new ScrapperConfig.Scheduler(false, Duration.ofMinutes(1), 50, 1),
            new ScrapperConfig.Database(ScrapperConfig.AccessType.SQL)
        );
        ClientConfig clientConfig = new ClientConfig();
        checker = new LinkUpdateChecker(
            new LinkParser(),
            clientConfig.gitHubClient(RestClient.builder(), config),
            clientConfig.stackOverflowClient(RestClient.builder(), config),
            config
        );
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @BeforeEach
    void resetWireMock() {
        WIRE_MOCK.resetAll();
    }

    @Test
    void githubNewIssueShouldBeFound() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo(GITHUB_ISSUES)).willReturn(okJson("""
            [
              {"title": "Падает сборка", "user": {"login": "octocat"},
               "created_at": "2026-11-05T10:00:00Z", "body": "После обновления не собирается", "number": 7}
            ]
            """)));

        List<ContentUpdate> updates = checker.getUpdates(GITHUB_LINK, SINCE);

        assertThat(updates).containsExactly(new ContentUpdate(
            UpdateType.ISSUE, "Падает сборка", "octocat",
            Instant.parse("2026-11-05T10:00:00Z"), "После обновления не собирается"
        ));
        WIRE_MOCK.verify(getRequestedFor(urlPathEqualTo(GITHUB_ISSUES))
            .withQueryParam("state", equalTo("all"))
            .withQueryParam("sort", equalTo("created"))
            .withHeader("Authorization", equalTo("Bearer test-github-token")));
    }

    @Test
    void githubPullRequestShouldBeRecognized() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo(GITHUB_ISSUES)).willReturn(okJson("""
            [
              {"title": "Добавить тесты", "user": {"login": "dev"}, "created_at": "2026-11-05T10:00:00Z",
               "body": null, "pull_request": {"url": "https://api.github.com/repos/user/repo/pulls/8"}}
            ]
            """)));

        List<ContentUpdate> updates = checker.getUpdates(GITHUB_LINK, SINCE);

        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).type()).isEqualTo(UpdateType.PULL_REQUEST);
        assertThat(updates.get(0).title()).isEqualTo("Добавить тесты");
        assertThat(updates.get(0).text()).isNull();
    }

    @Test
    void githubOldIssuesShouldBeSkippedAndNewSortedFromOldToNew() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo(GITHUB_ISSUES)).willReturn(okJson("""
            [
              {"title": "третий", "user": {"login": "a"}, "created_at": "2026-11-03T00:00:00Z"},
              {"title": "второй", "user": {"login": "a"}, "created_at": "2026-11-02T00:00:00Z"},
              {"title": "старый", "user": {"login": "a"}, "created_at": "2026-10-01T00:00:00Z"}
            ]
            """)));

        List<ContentUpdate> updates = checker.getUpdates(GITHUB_LINK, SINCE);

        assertThat(updates).extracting(ContentUpdate::title).containsExactly("второй", "третий");
    }

    @Test
    void firstCheckShouldReturnNothing() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo(GITHUB_ISSUES)).willReturn(okJson("""
            [{"title": "Issue", "user": {"login": "a"}, "created_at": "2026-11-03T00:00:00Z"}]
            """)));

        assertThat(checker.getUpdates(GITHUB_LINK, null)).isEmpty();
    }

    @Test
    void githubServerErrorShouldThrowCheckException() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo(GITHUB_ISSUES)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> checker.getUpdates(GITHUB_LINK, SINCE))
            .isInstanceOf(LinkCheckException.class)
            .hasMessageContaining("500");
    }

    @Test
    void githubNotFoundShouldThrowCheckException() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo(GITHUB_ISSUES)).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> checker.getUpdates(GITHUB_LINK, SINCE))
            .isInstanceOf(LinkCheckException.class)
            .hasMessageContaining("404");
    }

    @Test
    void githubRateLimitShouldThrowCheckException() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo(GITHUB_ISSUES)).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> checker.getUpdates(GITHUB_LINK, SINCE)).isInstanceOf(LinkCheckException.class);
    }

    @Test
    void unavailableServerShouldThrowCheckException() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo(GITHUB_ISSUES))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> checker.getUpdates(GITHUB_LINK, SINCE)).isInstanceOf(LinkCheckException.class);
    }

    @Test
    void githubBrokenJsonShouldThrowCheckException() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo(GITHUB_ISSUES)).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{это не json")));

        assertThatThrownBy(() -> checker.getUpdates(GITHUB_LINK, SINCE)).isInstanceOf(LinkCheckException.class);
    }

    @Test
    void githubIssueWithoutAuthorShouldHaveUnknownAuthor() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo(GITHUB_ISSUES)).willReturn(okJson("""
            [{"title": "Issue", "created_at": "2026-11-03T00:00:00Z"}]
            """)));

        assertThat(checker.getUpdates(GITHUB_LINK, SINCE).get(0).author()).isEqualTo("неизвестный пользователь");
    }

    private void stubQuestion() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/2.3/questions/123"))
            .withQueryParam("site", equalTo("stackoverflow"))
            .willReturn(okJson("""
                {"items": [{"question_id": 123, "title": "Как работает &quot;HashMap&quot;?", "last_activity_date": 1762000000}]}
                """)));
    }

    private void stubAnswers(String json) {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/2.3/questions/123/answers"))
            .withQueryParam("site", equalTo("stackoverflow"))
            .withQueryParam("filter", equalTo("withbody"))
            .willReturn(okJson(json)));
    }

    private void stubComments(String json) {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/2.3/questions/123/comments"))
            .withQueryParam("site", equalTo("stackoverflow"))
            .withQueryParam("filter", equalTo("withbody"))
            .willReturn(okJson(json)));
    }

    @Test
    void stackOverflowNewAnswerShouldBeFound() {
        stubQuestion();
        stubAnswers("""
            {"items": [{"owner": {"display_name": "Jon Skeet"}, "creation_date": 1793500000,
                        "body": "<p>Используйте <code>equals</code> &amp; <code>hashCode</code></p>"}]}
            """);
        stubComments("{\"items\": []}");

        List<ContentUpdate> updates = checker.getUpdates(SO_LINK, SINCE);

        assertThat(updates).containsExactly(new ContentUpdate(
            UpdateType.ANSWER, "Как работает \"HashMap\"?", "Jon Skeet",
            Instant.ofEpochSecond(1793500000L), "Используйте equals & hashCode"
        ));
    }

    @Test
    void stackOverflowNewCommentShouldBeFound() {
        stubQuestion();
        stubAnswers("{\"items\": []}");
        stubComments("""
            {"items": [{"owner": {"display_name": "user1"}, "creation_date": 1793500000, "body": "Уточните версию Java"}]}
            """);

        List<ContentUpdate> updates = checker.getUpdates(SO_LINK, SINCE);

        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).type()).isEqualTo(UpdateType.COMMENT);
        assertThat(updates.get(0).author()).isEqualTo("user1");
        assertThat(updates.get(0).text()).isEqualTo("Уточните версию Java");
    }

    @Test
    void stackOverflowWithoutNewPostsShouldNotRequestQuestion() {
        stubAnswers("""
            {"items": [{"owner": {"display_name": "old"}, "creation_date": 1700000000, "body": "старый ответ"}]}
            """);
        stubComments("{\"items\": []}");

        assertThat(checker.getUpdates(SO_LINK, SINCE)).isEmpty();
        WIRE_MOCK.verify(0, getRequestedFor(urlPathEqualTo("/2.3/questions/123")));
    }

    @Test
    void stackOverflowServerErrorShouldThrowCheckException() {
        stubComments("{\"items\": []}");
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/2.3/questions/123/answers")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> checker.getUpdates(SO_LINK, SINCE))
            .isInstanceOf(LinkCheckException.class)
            .hasMessageContaining("503");
    }

    @Test
    void stackOverflowWrongSchemaShouldThrowCheckException() {
        stubAnswers("{\"items\": \"не массив\"}");
        stubComments("{\"items\": []}");

        assertThatThrownBy(() -> checker.getUpdates(SO_LINK, SINCE)).isInstanceOf(LinkCheckException.class);
    }

    @Test
    void stackOverflowResponseWithoutItemsShouldThrowCheckException() {
        stubAnswers("{\"error_id\": 502, \"error_message\": \"throttle violation\"}");
        stubComments("{\"items\": []}");

        assertThatThrownBy(() -> checker.getUpdates(SO_LINK, SINCE)).isInstanceOf(LinkCheckException.class);
    }

    @Test
    void unsupportedLinkShouldThrowCheckException() {
        assertThatThrownBy(() -> checker.getUpdates("https://example.com/page", SINCE))
            .isInstanceOf(LinkCheckException.class);
    }
}
