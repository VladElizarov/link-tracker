package ru.tbank.bot.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import ru.tbank.bot.config.BotConfig;
import ru.tbank.bot.config.ClientConfig;
import ru.tbank.common.dto.AddLinkRequest;
import ru.tbank.common.dto.ListLinksResponse;
import ru.tbank.common.dto.RemoveLinkRequest;

class ScrapperClientTest {

    private WireMockServer wireMock;
    private ScrapperClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        BotConfig config = new BotConfig("test-token", wireMock.baseUrl());
        client = new ClientConfig().scrapperClient(RestClient.builder(), config);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void registerChatShouldSendPost() {
        wireMock.stubFor(post("/tg-chat/5").willReturn(aResponse().withStatus(200)));

        client.registerChat(5L);

        wireMock.verify(postRequestedFor(urlEqualTo("/tg-chat/5")));
    }

    @Test
    void addLinkShouldSendHeaderAndBody() {
        wireMock.stubFor(post("/links").willReturn(okJson("""
            {"id": 1, "url": "https://github.com/user/repo", "tags": ["работа"], "filters": []}
            """)));

        client.addLink(5L, new AddLinkRequest("https://github.com/user/repo", List.of("работа"), List.of()));

        wireMock.verify(postRequestedFor(urlEqualTo("/links"))
            .withHeader("Tg-Chat-Id", equalTo("5"))
            .withRequestBody(equalToJson("""
                {"link": "https://github.com/user/repo", "tags": ["работа"], "filters": []}
                """)));
    }

    @Test
    void getLinksShouldParseResponse() {
        wireMock.stubFor(get("/links").withHeader("Tg-Chat-Id", equalTo("5")).willReturn(okJson("""
            {"links": [{"id": 1, "url": "https://github.com/user/repo", "tags": [], "filters": []}], "size": 1}
            """)));

        ListLinksResponse response = client.getLinks(5L);

        assertThat(response.size()).isEqualTo(1);
        assertThat(response.links().get(0).url()).isEqualTo("https://github.com/user/repo");
    }

    @Test
    void removeLinkShouldSendDeleteWithBody() {
        wireMock.stubFor(delete("/links").willReturn(okJson("""
            {"id": 1, "url": "https://github.com/user/repo", "tags": [], "filters": []}
            """)));

        client.removeLink(5L, new RemoveLinkRequest("https://github.com/user/repo"));

        wireMock.verify(deleteRequestedFor(urlEqualTo("/links"))
            .withHeader("Tg-Chat-Id", equalTo("5"))
            .withRequestBody(equalToJson("{\"link\": \"https://github.com/user/repo\"}")));
    }

    @Test
    void conflictShouldBeThrownAsException() {
        wireMock.stubFor(post("/links").willReturn(aResponse().withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"description\": \"Ссылка уже отслеживается\", \"code\": \"409\"}")));

        assertThatThrownBy(() -> client.addLink(5L, new AddLinkRequest("https://github.com/user/repo", null, null)))
            .isInstanceOf(HttpClientErrorException.Conflict.class);
    }
}
