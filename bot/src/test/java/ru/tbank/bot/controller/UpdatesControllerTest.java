package ru.tbank.bot.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.tbank.bot.service.LinkUpdateHandler;
import ru.tbank.bot.service.TelegramBotService;

@WebMvcTest(controllers = UpdatesController.class, properties = "app.telegram-token=test")
@Import(LinkUpdateHandler.class)
class UpdatesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramBotService telegramBotService;

    @Test
    void correctUpdateShouldReturn200AndNotifyAllChats() throws Exception {
        String body = """
            {
              "id": 1,
              "url": "https://github.com/user/repo",
              "description": "Появилось обновление",
              "tgChatIds": [10, 20]
            }
            """;

        mockMvc.perform(post("/updates").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        verify(telegramBotService).sendNotification(eq(10L), contains("https://github.com/user/repo"));
        verify(telegramBotService).sendNotification(eq(20L), contains("https://github.com/user/repo"));
    }

    @Test
    void updateWithoutRequiredFieldsShouldReturn400() throws Exception {
        String body = """
            {"description": "нет id, url и tgChatIds"}
            """;

        mockMvc.perform(post("/updates").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("400"))
            .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"));

        verify(telegramBotService, never()).sendNotification(anyLong(), anyString());
    }

    @Test
    void updateWithWrongTypesShouldReturn400() throws Exception {
        String body = """
            {"id": "не число", "url": "https://github.com/user/repo", "tgChatIds": [1]}
            """;

        mockMvc.perform(post("/updates").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void brokenJsonShouldReturn400() throws Exception {
        mockMvc.perform(post("/updates").contentType(MediaType.APPLICATION_JSON).content("{это не json"))
            .andExpect(status().isBadRequest());
    }
}
