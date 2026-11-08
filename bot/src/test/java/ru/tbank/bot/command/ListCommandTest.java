package ru.tbank.bot.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.tbank.bot.TestData;
import ru.tbank.bot.client.ScrapperClient;
import ru.tbank.bot.model.IncomingMessage;

class ListCommandTest {

    private static final String GITHUB = "https://github.com/user/repo";
    private static final String STACKOVERFLOW = "https://stackoverflow.com/questions/123";

    private final ScrapperClient scrapperClient = mock(ScrapperClient.class);
    private final ListCommand command = new ListCommand(scrapperClient);

    @Test
    void listShouldShowAllLinks() {
        when(scrapperClient.getLinks(1L)).thenReturn(TestData.links(
            TestData.link(1, GITHUB, "работа"),
            TestData.link(2, STACKOVERFLOW)
        ));

        String answer = command.handle(new IncomingMessage(1L, "/list"));

        assertThat(answer)
            .contains("1. " + GITHUB + " (теги: работа)")
            .contains("2. " + STACKOVERFLOW);
    }

    @Test
    void listShouldShowSpecialMessageWhenEmpty() {
        when(scrapperClient.getLinks(1L)).thenReturn(TestData.links());

        String answer = command.handle(new IncomingMessage(1L, "/list"));

        assertThat(answer).isEqualTo(ListCommand.EMPTY_LIST_MESSAGE);
    }

    @Test
    void listWithTagShouldShowOnlyLinksWithThisTag() {
        when(scrapperClient.getLinks(1L)).thenReturn(TestData.links(
            TestData.link(1, GITHUB, "работа", "баг"),
            TestData.link(2, STACKOVERFLOW, "хобби")
        ));

        String answer = command.handle(new IncomingMessage(1L, "/list работа"));

        assertThat(answer).contains(GITHUB).doesNotContain(STACKOVERFLOW);
    }

    @Test
    void listWithUnknownTagShouldSayNothingFound() {
        when(scrapperClient.getLinks(1L)).thenReturn(TestData.links(TestData.link(1, GITHUB, "работа")));

        String answer = command.handle(new IncomingMessage(1L, "/list учёба"));

        assertThat(answer).isEqualTo("Нет отслеживаемых ссылок с тегом «учёба».");
    }

    @Test
    void listForUnregisteredChatShouldAskToStart() {
        when(scrapperClient.getLinks(1L)).thenThrow(TestData.httpError(HttpStatus.NOT_FOUND));

        String answer = command.handle(new IncomingMessage(1L, "/list"));

        assertThat(answer).isEqualTo(Messages.CHAT_NOT_REGISTERED);
    }
}
