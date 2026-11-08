package ru.tbank.bot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.tbank.bot.config.BotConfig;
import ru.tbank.bot.model.IncomingMessage;
import ru.tbank.bot.service.CommandDispatcher;
import ru.tbank.bot.service.TelegramBotService;

@SpringBootTest(properties = {
    "app.telegram-token=test-token",
    "app.scrapper-url=http://scrapper:8081",
    "app.message-transport=HTTP"
})
class BotApplicationTest {

    @MockitoBean
    private TelegramBotService telegramBotService;

    @Autowired
    private BotConfig botConfig;

    @Autowired
    private CommandDispatcher dispatcher;

    @Test
    void configShouldBeLoaded() {
        assertThat(botConfig.telegramToken()).isEqualTo("test-token");
        assertThat(botConfig.scrapperUrl()).isEqualTo("http://scrapper:8081");
    }

    @Test
    void dispatcherShouldKnowAllCommands() {
        String help = dispatcher.dispatch(new IncomingMessage(1L, "/help"));

        assertThat(help).contains("/start", "/help", "/track", "/untrack", "/list", "/cancel");
    }
}
