package ru.tbank.scrapper.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.tbank.scrapper.AbstractIntegrationTest;
import ru.tbank.scrapper.service.LinkService;

@AutoConfigureMockMvc
class ValkeyCacheTest extends AbstractIntegrationTest {

    private static final String LINK_1 = "https://github.com/user/one";
    private static final String LINK_2 = "https://github.com/user/two";

    private final RedisCommands<String, String> valkey = ValkeyTestContainer.testClient();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoSpyBean
    private LinkService linkService;

    @DynamicPropertySource
    static void valkeyProperties(DynamicPropertyRegistry registry) {
        ValkeyTestContainer.register(registry, "VALKEY");
        registry.add("app.cache.ttl", () -> "2s");
    }

    @BeforeEach
    void clearValkey() throws Exception {
        valkey.flushall();
        mockMvc.perform(post("/tg-chat/1")).andExpect(status().isOk());
    }

    private ResultActions getLinks() throws Exception {
        return mockMvc.perform(get("/links").header("Tg-Chat-Id", 1)).andExpect(status().isOk());
    }

    private ResultActions addLink(String link) throws Exception {
        return mockMvc.perform(post("/links").header("Tg-Chat-Id", 1)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"link\": \"" + link + "\", \"tags\": [\"работа\"]}"));
    }

    @Test
    void responseShouldBeStoredInValkeyAsJsonWithTtl() throws Exception {
        addLink(LINK_1).andExpect(status().isOk());

        String body = getLinks().andReturn().getResponse().getContentAsString();

        String cached = valkey.get("1");
        assertThat(objectMapper.readTree(cached)).isEqualTo(objectMapper.readTree(body));
        assertThat(cached).contains("\"url\":\"" + LINK_1 + "\"", "\"tags\":[\"работа\"]", "\"size\":1");
        assertThat(valkey.ttl("1")).isBetween(1L, 2L);
    }

    @Test
    void secondRequestShouldBeServedFromCache() throws Exception {
        addLink(LINK_1).andExpect(status().isOk());

        getLinks().andExpect(jsonPath("$.size").value(1));
        getLinks().andExpect(jsonPath("$.size").value(1));
        getLinks().andExpect(jsonPath("$.links[0].url").value(LINK_1));

        verify(linkService, times(1)).getAll(1L);
    }

    @Test
    void addingLinkShouldInvalidateCache() throws Exception {
        addLink(LINK_1).andExpect(status().isOk());
        getLinks();
        assertThat(valkey.exists("1")).isEqualTo(1);

        addLink(LINK_2).andExpect(status().isOk());

        assertThat(valkey.exists("1")).isZero();
        getLinks().andExpect(jsonPath("$.size").value(2));
        verify(linkService, times(2)).getAll(1L);
    }

    @Test
    void removingLinkShouldInvalidateCache() throws Exception {
        addLink(LINK_1).andExpect(status().isOk());
        getLinks().andExpect(jsonPath("$.size").value(1));

        mockMvc.perform(delete("/links").header("Tg-Chat-Id", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\": \"" + LINK_1 + "\"}"))
            .andExpect(status().isOk());

        assertThat(valkey.exists("1")).isZero();
        getLinks().andExpect(jsonPath("$.size").value(0));
    }

    @Test
    void changingTagsOfLinkShouldInvalidateCache() throws Exception {
        addLink(LINK_1).andExpect(status().isOk());
        getLinks().andExpect(jsonPath("$.links[0].tags.length()").value(1));

        mockMvc.perform(post("/links/tags").header("Tg-Chat-Id", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\": \"" + LINK_1 + "\", \"tag\": \"java\"}"))
            .andExpect(status().isOk());

        assertThat(valkey.exists("1")).isZero();
        getLinks().andExpect(jsonPath("$.links[0].tags.length()").value(2));
    }

    @Test
    void failedChangeShouldNotInvalidateCache() throws Exception {
        addLink(LINK_1).andExpect(status().isOk());
        getLinks();

        addLink(LINK_1).andExpect(status().isConflict());

        assertThat(valkey.exists("1")).isEqualTo(1);
    }

    @Test
    void deletingChatShouldInvalidateCache() throws Exception {
        getLinks();
        assertThat(valkey.exists("1")).isEqualTo(1);

        mockMvc.perform(delete("/tg-chat/1")).andExpect(status().isOk());

        assertThat(valkey.exists("1")).isZero();
        mockMvc.perform(get("/links").header("Tg-Chat-Id", 1)).andExpect(status().isNotFound());
    }

    @Test
    void recordShouldDisappearAfterTtl() throws Exception {
        addLink(LINK_1).andExpect(status().isOk());
        getLinks();

        await().atMost(Duration.ofSeconds(5)).until(() -> valkey.exists("1") == 0);

        getLinks().andExpect(jsonPath("$.size").value(1));
        verify(linkService, times(2)).getAll(1L);
    }

    @Test
    void unknownChatShouldNotBeCached() throws Exception {
        mockMvc.perform(get("/links").header("Tg-Chat-Id", 999)).andExpect(status().isNotFound());

        assertThat(valkey.exists("999")).isZero();
    }
}
