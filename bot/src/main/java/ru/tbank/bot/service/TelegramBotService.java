package ru.tbank.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.LinkPreviewOptions;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import ru.tbank.bot.command.Command;
import ru.tbank.bot.exception.NotificationSendException;
import ru.tbank.bot.model.IncomingMessage;

@Service
public class TelegramBotService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private final TelegramBot bot;
    private final CommandDispatcher dispatcher;
    private final List<Command> commands;

    public TelegramBotService(TelegramBot bot, CommandDispatcher dispatcher, List<Command> commands) {
        this.bot = bot;
        this.dispatcher = dispatcher;
        this.commands = commands;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        registerCommandsMenu();
        bot.setUpdatesListener(this::processUpdates, exception ->
            log.atError()
                .setCause(exception)
                .log("Ошибка при получении обновлений из Telegram"));
        log.info("Бот запущен и слушает сообщения");
    }

    public void registerCommandsMenu() {
        BotCommand[] botCommands = new BotCommand[commands.size()];
        for (int i = 0; i < commands.size(); i++) {
            Command command = commands.get(i);
            botCommands[i] = new BotCommand(command.name().substring(1), command.description());
        }

        BaseResponse response;
        try {
            response = bot.execute(new SetMyCommands(botCommands));
        } catch (RuntimeException e) {
            log.atWarn()
                .addKeyValue("error", e.getMessage())
                .log("Не удалось зарегистрировать меню команд: Telegram недоступен");
            return;
        }
        if (response.isOk()) {
            log.atInfo()
                .addKeyValue("commandsCount", botCommands.length)
                .log("Меню команд зарегистрировано");
        } else {
            log.atWarn()
                .addKeyValue("errorCode", response.errorCode())
                .addKeyValue("description", response.description())
                .log("Не удалось зарегистрировать меню команд");
        }
    }

    public int processUpdates(List<Update> updates) {
        for (Update update : updates) {
            try {
                processUpdate(update);
            } catch (Exception e) {
                log.atError()
                    .addKeyValue("updateId", update.updateId())
                    .setCause(e)
                    .log("Ошибка при обработке обновления");
            }
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    private void processUpdate(Update update) {
        Message message = update.message();
        if (message == null || message.text() == null) {
            return;
        }

        long chatId = message.chat().id();
        IncomingMessage incoming = new IncomingMessage(chatId, message.text());
        String answer = dispatcher.dispatch(incoming);
        sendMessage(chatId, answer);
    }

    public void sendMessage(long chatId, String text) {
        SendResponse response = execute(chatId, text);
        if (!response.isOk()) {
            log.atWarn()
                .addKeyValue("chatId", chatId)
                .addKeyValue("errorCode", response.errorCode())
                .addKeyValue("description", response.description())
                .log("Не удалось отправить сообщение");
        }
    }

    public void sendNotification(long chatId, String text) {
        SendResponse response = execute(chatId, text);
        if (!response.isOk()) {
            throw new NotificationSendException(
                "Telegram не принял сообщение для чата " + chatId + ": "
                    + response.errorCode() + " " + response.description());
        }
    }

    private SendResponse execute(long chatId, String text) {
        SendMessage request = new SendMessage(chatId, text)
            .linkPreviewOptions(new LinkPreviewOptions().isDisabled(true));
        return bot.execute(request);
    }
}
