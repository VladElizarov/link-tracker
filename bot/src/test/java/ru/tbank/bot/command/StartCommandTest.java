package ru.tbank.bot.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.tbank.bot.TestData;
import ru.tbank.bot.client.ScrapperClient;
import ru.tbank.bot.model.IncomingMessage;

class StartCommandTest {

    private final ScrapperClient scrapperClient = mock(ScrapperClient.class);
    private final StartCommand command = new StartCommand(scrapperClient);

    @Test
    void firstStartShouldRegisterChatAndGreetUser() {
        String answer = command.handle(new IncomingMessage(1L, "/start"));

        assertThat(answer).isEqualTo(StartCommand.WELCOME_MESSAGE);
        verify(scrapperClient).registerChat(1L);
    }

    @Test
    void secondStartShouldSayThatUserIsAlreadyRegistered() {
        doThrow(TestData.httpError(HttpStatus.CONFLICT)).when(scrapperClient).registerChat(1L);

        String answer = command.handle(new IncomingMessage(1L, "/start"));

        assertThat(answer).isEqualTo(StartCommand.ALREADY_REGISTERED_MESSAGE);
    }
}
