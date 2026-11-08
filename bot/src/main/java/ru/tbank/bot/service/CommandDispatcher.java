package ru.tbank.bot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import ru.tbank.bot.command.Command;
import ru.tbank.bot.command.Messages;
import ru.tbank.bot.dialog.TrackDialog;
import ru.tbank.bot.dialog.TrackDialogHandler;
import ru.tbank.bot.model.IncomingMessage;
import ru.tbank.bot.repository.DialogRepository;

@Service
public class CommandDispatcher {

    public static final String UNKNOWN_COMMAND_MESSAGE =
        "Неизвестная команда. Воспользуйтесь /help, чтобы посмотреть список доступных команд.";
    public static final String NOT_A_COMMAND_MESSAGE =
        "Я понимаю только команды. Воспользуйтесь /help, чтобы посмотреть список доступных команд.";

    private static final String CANCEL_COMMAND = "/cancel";

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final Map<String, Command> commandsByName = new HashMap<>();
    private final DialogRepository dialogRepository;
    private final TrackDialogHandler trackDialogHandler;

    public CommandDispatcher(
        List<Command> commands,
        DialogRepository dialogRepository,
        TrackDialogHandler trackDialogHandler
    ) {
        for (Command command : commands) {
            commandsByName.put(command.name(), command);
        }
        this.dialogRepository = dialogRepository;
        this.trackDialogHandler = trackDialogHandler;
    }

    public String dispatch(IncomingMessage message) {
        try {
            return doDispatch(message);
        } catch (RestClientException e) {
            log.atError()
                .addKeyValue("chatId", message.chatId())
                .addKeyValue("error", e.getMessage())
                .log("Ошибка при обращении к scrapper");
            dialogRepository.remove(message.chatId());
            return Messages.SERVICE_UNAVAILABLE;
        }
    }

    private String doDispatch(IncomingMessage message) {
        String text = message.text();
        if (text == null || text.isBlank()) {
            return NOT_A_COMMAND_MESSAGE;
        }

        if (!text.startsWith("/")) {
            TrackDialog dialog = dialogRepository.find(message.chatId());
            if (dialog != null) {
                return trackDialogHandler.handle(message, dialog);
            }
            return NOT_A_COMMAND_MESSAGE;
        }

        String commandName = extractCommandName(text);

        String prefix = "";
        if (!commandName.equals(CANCEL_COMMAND) && dialogRepository.remove(message.chatId()) != null) {
            log.atInfo()
                .addKeyValue("chatId", message.chatId())
                .addKeyValue("command", commandName)
                .log("Диалог /track прерван другой командой");
            prefix = Messages.TRACK_CANCELLED + "\n\n";
        }

        Command command = commandsByName.get(commandName);
        if (command == null) {
            log.atInfo()
                .addKeyValue("chatId", message.chatId())
                .addKeyValue("command", commandName)
                .log("Получена неизвестная команда");
            return prefix + UNKNOWN_COMMAND_MESSAGE;
        }

        log.atInfo()
            .addKeyValue("chatId", message.chatId())
            .addKeyValue("command", commandName)
            .log("Обработка команды");
        return prefix + command.handle(message);
    }

    private String extractCommandName(String text) {
        String firstWord = text.trim().split("\\s+")[0];
        int atIndex = firstWord.indexOf('@');
        if (atIndex != -1) {
            firstWord = firstWord.substring(0, atIndex);
        }
        return firstWord.toLowerCase();
    }
}
