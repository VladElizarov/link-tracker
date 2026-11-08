package ru.tbank.scrapper.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.tbank.scrapper.AbstractIntegrationTest;

@AutoConfigureMockMvc
public abstract class AbstractScrapperApiTest extends AbstractIntegrationTest {

    private static final String LINK = "https://github.com/user/repo";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions registerChat(long chatId) throws Exception {
        return mockMvc.perform(post("/tg-chat/" + chatId));
    }

    private ResultActions deleteChat(long chatId) throws Exception {
        return mockMvc.perform(delete("/tg-chat/" + chatId));
    }

    private ResultActions addLink(long chatId, String link) throws Exception {
        return mockMvc.perform(post("/links")
            .header("Tg-Chat-Id", chatId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"link": "%s", "tags": ["работа"], "filters": ["user:dummy"]}
                """.formatted(link)));
    }

    private ResultActions removeLink(long chatId, String link) throws Exception {
        return mockMvc.perform(delete("/links")
            .header("Tg-Chat-Id", chatId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"link\": \"%s\"}".formatted(link)));
    }

    private ResultActions getLinks(long chatId) throws Exception {
        return mockMvc.perform(get("/links").header("Tg-Chat-Id", chatId));
    }

    @Test
    void scenario31AddAndGetLink() throws Exception {
        registerChat(1).andExpect(status().isOk());

        addLink(1, LINK)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value(LINK))
            .andExpect(jsonPath("$.tags[0]").value("работа"))
            .andExpect(jsonPath("$.filters[0]").value("user:dummy"));

        getLinks(1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.links[*].url", hasItem(LINK)));
    }

    @Test
    void scenario32AddAndRemoveLink() throws Exception {
        registerChat(1).andExpect(status().isOk());
        addLink(1, LINK).andExpect(status().isOk());

        removeLink(1, LINK)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value(LINK));

        getLinks(1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(0))
            .andExpect(jsonPath("$.links[*].url", not(hasItem(LINK))));
    }

    @Test
    void scenario33RemoveLinkFromUnknownChat() throws Exception {
        registerChat(1).andExpect(status().isOk());
        addLink(1, LINK).andExpect(status().isOk());

        removeLink(999, LINK).andExpect(status().isNotFound());

        getLinks(1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.links[*].url", hasItem(LINK)));
    }

    @Test
    void scenario34AddLinkToUnknownChat() throws Exception {
        registerChat(1).andExpect(status().isOk());

        addLink(2, LINK)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("404"))
            .andExpect(jsonPath("$.exceptionName").value("ChatNotFoundException"));
    }

    @Test
    void scenario35WorkWithDeletedChat() throws Exception {
        registerChat(1).andExpect(status().isOk());
        deleteChat(1).andExpect(status().isOk());

        addLink(1, LINK).andExpect(status().isNotFound());
    }

    @Test
    void scenario36DeleteUnknownChat() throws Exception {
        deleteChat(1).andExpect(status().isNotFound());
    }

    @Test
    void registerSameChatTwiceShouldReturn409() throws Exception {
        registerChat(1).andExpect(status().isOk());
        registerChat(1).andExpect(status().isConflict());
    }

    @Test
    void addSameLinkTwiceShouldReturn409() throws Exception {
        registerChat(1).andExpect(status().isOk());
        addLink(1, LINK).andExpect(status().isOk());

        addLink(1, LINK).andExpect(status().isConflict());
    }

    @Test
    void unsupportedLinkShouldReturn400() throws Exception {
        registerChat(1).andExpect(status().isOk());

        addLink(1, "tbank://github.com/user/repo").andExpect(status().isBadRequest());
    }

    @Test
    void requestWithoutChatHeaderShouldReturn400() throws Exception {
        mockMvc.perform(get("/links")).andExpect(status().isBadRequest());
    }

    @Test
    void requestWithoutLinkShouldReturn400() throws Exception {
        registerChat(1).andExpect(status().isOk());

        mockMvc.perform(post("/links")
                .header("Tg-Chat-Id", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\": []}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void sameLinkInDifferentChatsShouldHaveSameId() throws Exception {
        registerChat(1).andExpect(status().isOk());
        registerChat(2).andExpect(status().isOk());

        addLink(1, LINK).andExpect(jsonPath("$.id").value(1));
        addLink(2, LINK).andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void tagsShouldBeManagedThroughApi() throws Exception {
        registerChat(1).andExpect(status().isOk());

        mockMvc.perform(post("/tags").header("Tg-Chat-Id", 1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"java\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/tags").header("Tg-Chat-Id", 1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"java\"}"))
            .andExpect(status().isConflict());

        mockMvc.perform(put("/tags/java").header("Tg-Chat-Id", 1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"kotlin\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/tags").header("Tg-Chat-Id", 1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", contains("kotlin")));

        mockMvc.perform(delete("/tags/kotlin").header("Tg-Chat-Id", 1)).andExpect(status().isOk());
        mockMvc.perform(delete("/tags/kotlin").header("Tg-Chat-Id", 1)).andExpect(status().isNotFound());
    }

    @Test
    void tagShouldBeAttachedToLinkThroughApi() throws Exception {
        registerChat(1).andExpect(status().isOk());
        addLink(1, LINK).andExpect(status().isOk());

        mockMvc.perform(post("/links/tags").header("Tg-Chat-Id", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\": \"%s\", \"tag\": \"java\"}".formatted(LINK)))
            .andExpect(status().isOk());

        getLinks(1).andExpect(jsonPath("$.links[0].tags", contains("java", "работа")));
    }

    @Test
    void dataShouldBeStoredInDatabase() throws Exception {
        registerChat(1).andExpect(status().isOk());
        addLink(1, LINK).andExpect(status().isOk());

        String url = jdbcClient.sql("""
                SELECT l.url FROM subscriptions s JOIN links l ON l.id = s.link_id WHERE s.chat_id = 1
                """)
            .query(String.class).single();
        org.assertj.core.api.Assertions.assertThat(url).isEqualTo(LINK);
    }
}
