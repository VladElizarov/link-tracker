package ru.tbank.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import ru.tbank.bot.TestData;
import ru.tbank.bot.client.ScrapperClient;
import ru.tbank.bot.command.Messages;
import ru.tbank.bot.command.StartCommand;
import ru.tbank.bot.command.TrackCommand;
import ru.tbank.bot.config.TelegramConfig;
import ru.tbank.bot.dialog.TrackDialogHandler;
import ru.tbank.bot.model.IncomingMessage;
import ru.tbank.bot.repository.DialogRepository;
import ru.tbank.bot.repository.InMemoryDialogRepository;
import ru.tbank.common.dto.AddLinkRequest;

class CommandDispatcherTest {

    private static final long CHAT_ID = 1L;
    private static final String GITHUB = "https://github.com/user/repo";

    private ScrapperClient scrapperClient;
    private DialogRepository dialogRepository;
    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        scrapperClient = mock(ScrapperClient.class);
        dialogRepository = new InMemoryDialogRepository();
        dispatcher = new CommandDispatcher(
            TelegramConfig.createCommands(scrapperClient, dialogRepository),
            dialogRepository,
            new TrackDialogHandler(dialogRepository, scrapperClient)
        );
        when(scrapperClient.getLinks(anyLong())).thenReturn(TestData.links());
    }

    private String send(String text) {
        return dispatcher.dispatch(new IncomingMessage(CHAT_ID, text));
    }

    @Test
    void startShouldReturnWelcomeMessage() {
        assertThat(send("/start")).isEqualTo(StartCommand.WELCOME_MESSAGE);
    }

    @Test
    void helpShouldReturnListOfCommands() {
        assertThat(send("/help")).contains("/start", "/help", "/track", "/untrack", "/list", "/cancel");
    }

    @Test
    void commandWithBotNameShouldWork() {
        assertThat(send("/help@LinkTrackerBot")).contains("/start", "/help");
    }

    @Test
    void unknownCommandShouldReturnError() {
        assertThat(send("/unknown")).isEqualTo(CommandDispatcher.UNKNOWN_COMMAND_MESSAGE);
    }

    @Test
    void plainTextWithoutDialogShouldReturnHint() {
        assertThat(send("привет")).isEqualTo(CommandDispatcher.NOT_A_COMMAND_MESSAGE);
    }

    @Test
    void trackDialogShouldAddLinkWithTagsAndFilters() {
        assertThat(send("/track")).isEqualTo(TrackCommand.ASK_LINK_MESSAGE);
        assertThat(send(GITHUB)).isEqualTo(TrackDialogHandler.ASK_TAGS_MESSAGE);
        assertThat(send("работа, баг")).isEqualTo(TrackDialogHandler.ASK_FILTERS_MESSAGE);
        String answer = send("user:dummy");

        assertThat(answer).contains("Ссылка добавлена: " + GITHUB, "работа, баг", "user:dummy");
        verify(scrapperClient).addLink(CHAT_ID,
            new AddLinkRequest(GITHUB, List.of("работа", "баг"), List.of("user:dummy")));
        assertThat(dialogRepository.find(CHAT_ID)).isNull();
    }

    @Test
    void tagsAndFiltersCanBeSkipped() {
        send("/track");
        send(GITHUB);
        send("-");
        send("-");

        verify(scrapperClient).addLink(CHAT_ID, new AddLinkRequest(GITHUB, List.of(), List.of()));
    }

    @Test
    void invalidLinkShouldBeRejectedAndDialogShouldContinue() {
        send("/track");

        String answer = send("tbank://github.com/user/repo");

        assertThat(answer).isEqualTo(Messages.INVALID_LINK);
        assertThat(send(GITHUB)).isEqualTo(TrackDialogHandler.ASK_TAGS_MESSAGE);
    }

    @Test
    void alreadyTrackedLinkShouldBeReported() {
        when(scrapperClient.getLinks(CHAT_ID)).thenReturn(TestData.links(TestData.link(1, GITHUB)));
        send("/track");

        String answer = send(GITHUB);

        assertThat(answer).isEqualTo(Messages.LINK_ALREADY_TRACKED);
        verify(scrapperClient, never()).addLink(anyLong(), any());
        assertThat(dialogRepository.find(CHAT_ID)).isNull();
    }

    @Test
    void conflictFromScrapperShouldBeReportedAsAlreadyTracked() {
        when(scrapperClient.addLink(eq(CHAT_ID), any())).thenThrow(TestData.httpError(HttpStatus.CONFLICT));
        send("/track");
        send(GITHUB);
        send("-");

        assertThat(send("-")).isEqualTo(Messages.LINK_ALREADY_TRACKED);
    }

    @Test
    void cancelShouldStopDialog() {
        send("/track");

        assertThat(send("/cancel")).isEqualTo(Messages.TRACK_CANCELLED);
        assertThat(send(GITHUB)).isEqualTo(CommandDispatcher.NOT_A_COMMAND_MESSAGE);
        verify(scrapperClient, never()).addLink(anyLong(), any());
    }

    @Test
    void otherCommandShouldCancelDialog() {
        send("/track");
        send(GITHUB);

        String answer = send("/help");

        assertThat(answer).startsWith(Messages.TRACK_CANCELLED).contains("Доступные команды:");
        assertThat(dialogRepository.find(CHAT_ID)).isNull();
    }

    @Test
    void scrapperUnavailableShouldNotBreakBot() {
        when(scrapperClient.getLinks(CHAT_ID)).thenThrow(new ResourceAccessException("Connection refused"));

        assertThat(send("/list")).isEqualTo(Messages.SERVICE_UNAVAILABLE);
    }
}
