package ru.tbank.scrapper.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.config.ClientConfig;
import ru.tbank.scrapper.config.ScrapperConfig;

class BotClientTest {

    private WireMockServer wireMock;
    private BotClient botClient;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        ScrapperConfig config = new ScrapperConfig(
            wireMock.baseUrl(),
            new ScrapperConfig.GitHub("http://unused", null),
            new ScrapperConfig.StackOverflow("http://unused", null),
            new ScrapperConfig.Scheduler(false, Duration.ofMinutes(1), 50, 1),
            new ScrapperConfig.Database(ScrapperConfig.AccessType.SQL)
        );
        botClient = new ClientConfig().botClient(RestClient.builder(), config);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void sendUpdateShouldPostLinkUpdate() {
        wireMock.stubFor(post("/updates").willReturn(aResponse().withStatus(200)));

        botClient.sendUpdate(new LinkUpdate(7L, "https://github.com/user/repo", "Появилось обновление", List.of(1L, 3L)));

        wireMock.verify(postRequestedFor(urlEqualTo("/updates")).withRequestBody(equalToJson("""
            {"id": 7, "url": "https://github.com/user/repo", "description": "Появилось обновление", "tgChatIds": [1, 3]}
            """)));
    }
}
