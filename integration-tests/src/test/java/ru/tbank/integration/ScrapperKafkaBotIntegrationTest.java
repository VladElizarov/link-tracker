package ru.tbank.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ru.tbank.bot.BotApplication;
import ru.tbank.common.dto.AddLinkRequest;
import ru.tbank.scrapper.ScrapperApplication;
import ru.tbank.scrapper.config.ScrapperConfig;
import ru.tbank.scrapper.repository.LinkRepository;
import ru.tbank.scrapper.repository.OutboxRepository;
import ru.tbank.scrapper.scheduler.LinkUpdateScheduler;
import ru.tbank.scrapper.service.LinkCheckService;

class ScrapperKafkaBotIntegrationTest {

    private static final String TELEGRAM_TOKEN = "e2e-token";
    private static final long CHAT_ID = 42L;
    private static final String GITHUB_LINK = "https://github.com/user/repo";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
        .withDatabaseName("scrapper");
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");
    private static final WireMockServer WIRE_MOCK = new WireMockServer(wireMockConfig().dynamicPort());

    private static ConfigurableApplicationContext scrapper;
    private static ConfigurableApplicationContext bot;
    private static String scrapperUrl;

    @BeforeAll
    static void startEverything() {
        POSTGRES.start();
        KAFKA.start();
        WIRE_MOCK.start();
        runMigrations();
        stubTelegram();

        scrapper = new SpringApplicationBuilder(ScrapperApplication.class).run(
            "--spring.config.location=" + configFile("scrapper"),
            "--server.port=0",
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + POSTGRES.getUsername(),
            "--spring.datasource.password=" + POSTGRES.getPassword(),
            "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
            "--app.message-transport=KAFKA",
            "--app.kafka.replicas=1",
            "--app.kafka.min-insync-replicas=1",
            "--app.github.url=" + WIRE_MOCK.baseUrl(),
            "--app.scheduler.enable=false",
            "--app.outbox.enable=true",
            "--app.outbox.interval=200ms",
            "--app.cache.mode=NONE"
        );
        scrapperUrl = "http://localhost:" + scrapper.getEnvironment().getProperty("local.server.port");

        bot = new SpringApplicationBuilder(BotApplication.class).run(
            "--spring.config.location=" + configFile("bot"),
            "--server.port=0",
            "--app.telegram-token=" + TELEGRAM_TOKEN,
            "--app.telegram-api-url=" + WIRE_MOCK.baseUrl() + "/bot",
            "--app.scrapper-url=" + scrapperUrl,
            "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
            "--app.message-transport=KAFKA",
            "--app.kafka.replicas=1",
            "--app.kafka.min-insync-replicas=1",
            "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
        );
    }

    @AfterAll
    static void stopEverything() {
        if (bot != null) {
            bot.close();
        }
        if (scrapper != null) {
            scrapper.close();
        }
        WIRE_MOCK.stop();
        KAFKA.stop();
        POSTGRES.stop();
    }

    @Test
    void newGithubIssueShouldReachTelegramUserThroughKafka() {
        RestClient client = RestClient.create(scrapperUrl);
        client.post().uri("/tg-chat/{id}", CHAT_ID).retrieve().toBodilessEntity();
        client.post().uri("/links")
            .header("Tg-Chat-Id", String.valueOf(CHAT_ID))
            .body(new AddLinkRequest(GITHUB_LINK, List.of(), List.of()))
            .retrieve()
            .toBodilessEntity();

        WIRE_MOCK.stubFor(any(urlPathEqualTo("/repos/user/repo/issues")).willReturn(okJson("[]")));
        LinkUpdateScheduler scheduler = createScheduler();
        scheduler.update();

        String createdAt = Instant.now().plus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();
        WIRE_MOCK.stubFor(any(urlPathEqualTo("/repos/user/repo/issues")).willReturn(okJson("""
            [{"title": "Сборка падает на Java 25", "user": {"login": "octocat"},
              "created_at": "%s", "body": "Шаги: mvn clean test"}]
            """.formatted(createdAt))));
        scheduler.update();

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            List<String> messages = sentTelegramMessages();
            assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("chat_id=" + CHAT_ID)
                .contains("Обновление по ссылке " + GITHUB_LINK)
                .contains("Новый Issue")
                .contains("Название: Сборка падает на Java 25")
                .contains("Автор: octocat")
                .contains("Описание: Шаги: mvn clean test"));
        });

        JdbcClient jdbcClient = scrapper.getBean(JdbcClient.class);
        assertThat(jdbcClient.sql("SELECT status FROM outbox").query(String.class).list()).containsExactly("SENT");
        assertThat(sentTelegramMessages()).filteredOn(message -> message.contains("Новый Issue")).hasSize(1);
    }

    private static LinkUpdateScheduler createScheduler() {
        return new LinkUpdateScheduler(
            scrapper.getBean(LinkRepository.class),
            scrapper.getBean(LinkCheckService.class),
            scrapper.getBean(OutboxRepository.class),
            scrapper.getBean(ScrapperConfig.class)
        );
    }

    private static void stubTelegram() {
        String prefix = "/bot" + TELEGRAM_TOKEN;
        WIRE_MOCK.stubFor(any(urlPathEqualTo(prefix + "/getUpdates"))
            .willReturn(okJson("{\"ok\": true, \"result\": []}").withFixedDelay(500)));
        WIRE_MOCK.stubFor(any(urlPathEqualTo(prefix + "/setMyCommands"))
            .willReturn(okJson("{\"ok\": true, \"result\": true}")));
        WIRE_MOCK.stubFor(any(urlPathEqualTo(prefix + "/sendMessage")).willReturn(okJson("""
            {"ok": true, "result": {"message_id": 1, "date": 0, "chat": {"id": 42, "type": "private"}, "text": "ok"}}
            """)));
        WIRE_MOCK.stubFor(any(urlPathMatching(prefix + "/.*")).atPriority(10)
            .willReturn(okJson("{\"ok\": true, \"result\": true}")));
    }

    private static List<String> sentTelegramMessages() {
        List<String> result = new ArrayList<>();
        for (LoggedRequest request : WIRE_MOCK.findAll(postRequestedFor(urlPathEqualTo("/bot" + TELEGRAM_TOKEN + "/sendMessage")))) {
            result.add(URLDecoder.decode(request.getBodyAsString(), StandardCharsets.UTF_8));
        }
        return result;
    }

    private static String configFile(String module) {
        Path path = Path.of("..", module, "src", "main", "resources", "application.yaml");
        if (!Files.exists(path)) {
            path = Path.of(module, "src", "main", "resources", "application.yaml");
        }
        return "file:" + path.toAbsolutePath().normalize();
    }

    private static void runMigrations() {
        Path migrations = Path.of("..", "migrations");
        if (!Files.isDirectory(migrations)) {
            migrations = Path.of("migrations");
        }
        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            new Liquibase("master.xml", new DirectoryResourceAccessor(migrations.toAbsolutePath().normalize()), database)
                .update(new Contexts(), new LabelExpression());
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось применить миграции", e);
        }
    }
}
