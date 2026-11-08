package ru.tbank.scrapper.scheduler;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.AbstractIntegrationTest;
import ru.tbank.scrapper.config.ScrapperConfig;
import ru.tbank.scrapper.model.OutboxMessage;
import ru.tbank.scrapper.repository.ChatRepository;
import ru.tbank.scrapper.repository.LinkRepository;
import ru.tbank.scrapper.repository.OutboxRepository;
import ru.tbank.scrapper.service.LinkCheckService;

@TestPropertySource(properties = {"app.scheduler.batch-size=50", "app.scheduler.threads=4"})
class SchedulerIntegrationTest extends AbstractIntegrationTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    private static final String GITHUB = "https://github.com/user/repo";
    private static final String STACKOVERFLOW = "https://stackoverflow.com/questions/123";

    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private LinkRepository linkRepository;
    @Autowired
    private LinkCheckService linkCheckService;
    @Autowired
    private ScrapperConfig config;
    @Autowired
    private OutboxRepository outboxRepository;

    private LinkUpdateScheduler scheduler;

    @DynamicPropertySource
    static void apiProperties(DynamicPropertyRegistry registry) {
        registry.add("app.github.url", WIRE_MOCK::baseUrl);
        registry.add("app.stack-overflow.url", WIRE_MOCK::baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @BeforeEach
    void setUp() {
        WIRE_MOCK.resetAll();
        scheduler = new LinkUpdateScheduler(linkRepository, linkCheckService, outboxRepository, config);
        chatRepository.save(1L);
        chatRepository.save(2L);
    }

    private static String inOneMinute() {
        return Instant.now().plus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    @Test
    void newGithubIssueShouldBeSentWithTitleAuthorAndPreview() {
        linkRepository.addSubscription(1L, GITHUB, List.of(), List.of());
        linkRepository.addSubscription(2L, GITHUB, List.of(), List.of());
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/repos/user/repo/issues")).willReturn(okJson("[]")));

        scheduler.update();
        assertThat(outboxRepository.findNew(10)).isEmpty();

        String longBody = "Шаги воспроизведения. ".repeat(20);
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/repos/user/repo/issues")).willReturn(okJson("""
            [{"title": "Падает сборка", "user": {"login": "octocat"}, "created_at": "%s", "body": "%s"}]
            """.formatted(inOneMinute(), longBody))));
        scheduler.update();

        List<OutboxMessage> outbox = outboxRepository.findNew(10);
        assertThat(outbox).hasSize(1);
        LinkUpdate update = outbox.get(0).update();
        assertThat(update.url()).isEqualTo(GITHUB);
        assertThat(update.tgChatIds()).containsExactly(1L, 2L);
        assertThat(update.description())
            .contains("Новый Issue", "Название: Падает сборка", "Автор: octocat", "Создан: ")
            .contains("Описание: " + longBody.substring(0, 200).trim())
            .doesNotContain(longBody.trim());

        scheduler.update();
        assertThat(outboxRepository.findNew(10)).hasSize(1);
    }

    @Test
    void newStackOverflowAnswerShouldBeSent() {
        linkRepository.addSubscription(1L, STACKOVERFLOW, List.of(), List.of());
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/2.3/questions/123/answers")).willReturn(okJson("{\"items\": []}")));
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/2.3/questions/123/comments")).willReturn(okJson("{\"items\": []}")));
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/2.3/questions/123")).willReturn(okJson("""
            {"items": [{"question_id": 123, "title": "Как работает HashMap?"}]}
            """)));
        scheduler.update();

        long inOneMinute = Instant.now().plus(1, ChronoUnit.MINUTES).getEpochSecond();
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/2.3/questions/123/answers")).willReturn(okJson("""
            {"items": [{"owner": {"display_name": "Jon Skeet"}, "creation_date": %d, "body": "<p>Смотрите hashCode</p>"}]}
            """.formatted(inOneMinute))));
        scheduler.update();

        List<OutboxMessage> outbox = outboxRepository.findNew(10);
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).update().tgChatIds()).containsExactly(1L);
        assertThat(outbox.get(0).update().description())
            .contains("Новый ответ на вопрос", "Вопрос: Как работает HashMap?", "Автор: Jon Skeet",
                "Ответ: Смотрите hashCode");
    }

    @Test
    void batchWithBrokenLinksShouldProcessOthersAndReportBroken() {
        for (int i = 0; i < 120; i++) {
            linkRepository.addSubscription(1L, "https://github.com/user/repo" + i, List.of(), List.of());
        }
        WIRE_MOCK.stubFor(get(urlPathMatching("/repos/user/repo\\d+/issues")).atPriority(10)
            .willReturn(okJson("[]")));
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/repos/user/repo5/issues")).atPriority(1)
            .willReturn(aResponse().withStatus(500)));
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/repos/user/repo60/issues")).atPriority(1)
            .willReturn(aResponse().withStatus(404)));
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/repos/user/repo110/issues")).atPriority(1)
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{битый json")));

        scheduler.update();

        WIRE_MOCK.verify(120, getRequestedFor(urlPathMatching("/repos/user/repo\\d+/issues")));
        Long remembered = jdbcClient.sql("SELECT count(*) FROM links WHERE last_update IS NOT NULL")
            .query(Long.class).single();
        Long checked = jdbcClient.sql("SELECT count(*) FROM links WHERE last_checked_at IS NOT NULL")
            .query(Long.class).single();
        assertThat(remembered).isEqualTo(117);
        assertThat(checked).isEqualTo(120);

        List<LinkUpdate> reports = new ArrayList<>();
        for (OutboxMessage message : outboxRepository.findNew(100)) {
            reports.add(message.update());
        }
        assertThat(reports).extracting(LinkUpdate::url).containsExactlyInAnyOrder(
            "https://github.com/user/repo5", "https://github.com/user/repo60", "https://github.com/user/repo110");
        assertThat(reports).allSatisfy(update ->
            assertThat(update.description()).startsWith("Отчёт: не удалось проверить ссылку"));

        scheduler.update();
        assertThat(outboxRepository.findNew(100)).hasSize(3);
    }
}
