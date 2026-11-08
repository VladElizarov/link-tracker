package ru.tbank.scrapper.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.tbank.scrapper.AbstractIntegrationTest;

@AutoConfigureMockMvc
class ClientSideCacheTest extends AbstractIntegrationTest {

    private static final String LINK_1 = "https://github.com/user/one";

    private final RedisCommands<String, String> valkey = ValkeyTestContainer.testClient();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LinkListCache cache;

    @DynamicPropertySource
    static void valkeyProperties(DynamicPropertyRegistry registry) {
        ValkeyTestContainer.register(registry, "CLIENT_SIDE");
    }

    @BeforeEach
    void clear() throws Exception {
        valkey.flushall();
        mockMvc.perform(post("/tg-chat/1")).andExpect(status().isOk());
        mockMvc.perform(post("/links").header("Tg-Chat-Id", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\": \"" + LINK_1 + "\"}"))
            .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(5)).until(() -> localCache().localSize() == 0);
        valkey.configResetstat();
    }

    private ClientSideLinkListCache localCache() {
        return (ClientSideLinkListCache) cache;
    }

    private ResultActions getLinks() throws Exception {
        return mockMvc.perform(get("/links").header("Tg-Chat-Id", 1)).andExpect(status().isOk());
    }

    private long serverGetCalls() {
        for (String line : valkey.info("commandstats").split("\r?\n")) {
            if (line.startsWith("cmdstat_get:")) {
                return Long.parseLong(line.split("calls=")[1].split(",")[0]);
            }
        }
        return 0;
    }

    @Test
    void trackingShouldBeEnabledForApplicationConnection() {
        assertThat(valkey.clientList()).containsPattern("flags=t\\b");
    }

    @Test
    void repeatedReadsShouldNotGoToServer() throws Exception {
        getLinks();
        getLinks();
        long afterWarmUp = serverGetCalls();

        for (int i = 0; i < 10; i++) {
            getLinks().andExpect(jsonPath("$.links[0].url").value(LINK_1));
        }

        assertThat(afterWarmUp).isEqualTo(2);
        assertThat(serverGetCalls()).isEqualTo(afterWarmUp);
        assertThat(localCache().localSize()).isEqualTo(1);
    }

    @Test
    void changeOnServerShouldInvalidateLocalCopy() throws Exception {
        getLinks();
        getLinks();
        assertThat(localCache().localSize()).isEqualTo(1);

        valkey.set("1", "{\"links\":[],\"size\":0}");

        await().atMost(Duration.ofSeconds(5)).until(() -> localCache().localSize() == 0);
        getLinks().andExpect(jsonPath("$.size").value(0));
    }

    @Test
    void addingLinkShouldInvalidateLocalCopy() throws Exception {
        getLinks();
        getLinks().andExpect(jsonPath("$.size").value(1));

        mockMvc.perform(post("/links").header("Tg-Chat-Id", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\": \"https://github.com/user/two\"}"))
            .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(5)).until(() -> localCache().localSize() == 0);
        getLinks().andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void expiredKeyShouldInvalidateLocalCopy() throws Exception {
        getLinks();
        getLinks();
        assertThat(localCache().localSize()).isEqualTo(1);

        valkey.pexpire("1", 50);

        await().atMost(Duration.ofSeconds(5)).until(() -> localCache().localSize() == 0);
    }
}
