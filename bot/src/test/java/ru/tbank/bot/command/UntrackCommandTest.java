package ru.tbank.bot.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.tbank.bot.TestData;
import ru.tbank.bot.client.ScrapperClient;
import ru.tbank.bot.model.IncomingMessage;
import ru.tbank.common.dto.RemoveLinkRequest;

class UntrackCommandTest {

    private static final String GITHUB = "https://github.com/user/repo";

    private final ScrapperClient scrapperClient = mock(ScrapperClient.class);
    private final UntrackCommand command = new UntrackCommand(scrapperClient);

    @Test
    void untrackShouldRemoveLink() {
        String answer = command.handle(new IncomingMessage(1L, "/untrack " + GITHUB));

        assertThat(answer).isEqualTo("Ссылка " + GITHUB + " больше не отслеживается.");
        verify(scrapperClient).removeLink(1L, new RemoveLinkRequest(GITHUB));
    }

    @Test
    void untrackWithoutLinkShouldShowUsage() {
        String answer = command.handle(new IncomingMessage(1L, "/untrack"));

        assertThat(answer).isEqualTo(UntrackCommand.USAGE_MESSAGE);
        verifyNoInteractions(scrapperClient);
    }

    @Test
    void untrackOfUnknownLinkShouldSayNotFound() {
        when(scrapperClient.removeLink(eq(1L), any())).thenThrow(TestData.httpError(HttpStatus.NOT_FOUND));

        String answer = command.handle(new IncomingMessage(1L, "/untrack " + GITHUB));

        assertThat(answer).isEqualTo(UntrackCommand.NOT_FOUND_MESSAGE);
    }
}
