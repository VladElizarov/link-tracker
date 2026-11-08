package ru.tbank.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import com.pengrad.telegrambot.utility.BotUtils;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.tbank.bot.client.ScrapperClient;
import ru.tbank.bot.command.Command;
import ru.tbank.bot.command.StartCommand;
import ru.tbank.bot.config.TelegramConfig;
import ru.tbank.bot.dialog.TrackDialogHandler;
import ru.tbank.bot.exception.NotificationSendException;
import ru.tbank.bot.repository.DialogRepository;
import ru.tbank.bot.repository.InMemoryDialogRepository;

class TelegramBotServiceTest {

    private TelegramBot bot;
    private TelegramBotService service;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBot.class);
        ScrapperClient scrapperClient = mock(ScrapperClient.class);
        DialogRepository dialogRepository = new InMemoryDialogRepository();
        List<Command> commands = TelegramConfig.createCommands(scrapperClient, dialogRepository);
        CommandDispatcher dispatcher = new CommandDispatcher(
            commands, dialogRepository, new TrackDialogHandler(dialogRepository, scrapperClient));
        service = new TelegramBotService(bot, dispatcher, commands);

        SendResponse okSend = mock(SendResponse.class);
        when(okSend.isOk()).thenReturn(true);
        when(bot.execute(any(SendMessage.class))).thenReturn(okSend);

        BaseResponse okBase = mock(BaseResponse.class);
        when(okBase.isOk()).thenReturn(true);
        when(bot.execute(any(SetMyCommands.class))).thenReturn(okBase);
    }

    @Test
    void startUpdateShouldBeAnsweredWithGreeting() {
        int result = service.processUpdates(List.of(updateWithText(42L, "/start")));

        SendMessage sent = captureSentMessage();
        assertThat(sent.getParameters().get("chat_id")).isEqualTo(42L);
        assertThat(sent.getParameters().get("text")).isEqualTo(StartCommand.WELCOME_MESSAGE);
        assertThat(result).isEqualTo(UpdatesListener.CONFIRMED_UPDATES_ALL);
    }

    @Test
    void helpUpdateShouldBeAnsweredWithCommandList() {
        service.processUpdates(List.of(updateWithText(42L, "/help")));

        String text = (String) captureSentMessage().getParameters().get("text");
        assertThat(text).contains("/start", "/help");
    }

    @Test
    void unknownCommandUpdateShouldBeAnsweredWithError() {
        service.processUpdates(List.of(updateWithText(42L, "/abracadabra")));

        assertThat(captureSentMessage().getParameters().get("text"))
            .isEqualTo(CommandDispatcher.UNKNOWN_COMMAND_MESSAGE);
    }

    @Test
    void registerCommandsMenuShouldSendAllCommands() {
        service.registerCommandsMenu();

        ArgumentCaptor<SetMyCommands> captor = ArgumentCaptor.forClass(SetMyCommands.class);
        verify(bot).execute(captor.capture());
        BotCommand[] botCommands = (BotCommand[]) captor.getValue().getParameters().get("commands");
        assertThat(botCommands).extracting(BotCommand::command)
            .containsExactly("start", "help", "track", "untrack", "list", "cancel");
    }

    @Test
    void unavailableTelegramOnStartShouldNotBreakBot() {
        when(bot.execute(any(SetMyCommands.class))).thenThrow(new RuntimeException("Failed to connect"));

        assertThatCode(() -> service.registerCommandsMenu()).doesNotThrowAnyException();
    }

    @Test
    void notificationShouldBeSentToChat() {
        service.sendNotification(42L, "Новый Issue");

        SendMessage sent = captureSentMessage();
        assertThat(sent.getParameters()).containsEntry("chat_id", 42L).containsEntry("text", "Новый Issue");
    }

    @Test
    void failedNotificationShouldThrowSoKafkaCanRetry() {
        SendResponse failed = mock(SendResponse.class);
        when(failed.isOk()).thenReturn(false);
        when(failed.errorCode()).thenReturn(502);
        when(bot.execute(any(SendMessage.class))).thenReturn(failed);

        assertThatThrownBy(() -> service.sendNotification(42L, "текст"))
            .isInstanceOf(NotificationSendException.class)
            .hasMessageContaining("502");
    }

    private SendMessage captureSentMessage() {
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(captor.capture());
        return captor.getValue();
    }

    private Update updateWithText(long chatId, String text) {
        String json = """
            {
              "update_id": 1,
              "message": {
                "message_id": 1,
                "date": 0,
                "chat": {"id": %d, "type": "private"},
                "text": "%s"
              }
            }
            """.formatted(chatId, text);
        return BotUtils.parseUpdate(json);
    }
}
