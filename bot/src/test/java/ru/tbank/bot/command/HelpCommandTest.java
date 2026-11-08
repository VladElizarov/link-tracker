package ru.tbank.bot.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.tbank.bot.client.ScrapperClient;
import ru.tbank.bot.config.TelegramConfig;
import ru.tbank.bot.model.IncomingMessage;
import ru.tbank.bot.repository.InMemoryDialogRepository;

class HelpCommandTest {

    @Test
    void helpShouldListAllCommandsWithDescriptions() {
        List<Command> commands = TelegramConfig.createCommands(
            mock(ScrapperClient.class), new InMemoryDialogRepository());
        Command help = commands.stream().filter(c -> c.name().equals("/help")).findFirst().orElseThrow();

        String answer = help.handle(new IncomingMessage(1L, "/help"));

        assertThat(answer)
            .startsWith("Доступные команды:")
            .contains("/start - начать работу с ботом")
            .contains("/help - показать список доступных команд")
            .contains("/track", "/untrack", "/list", "/cancel");
    }
}
