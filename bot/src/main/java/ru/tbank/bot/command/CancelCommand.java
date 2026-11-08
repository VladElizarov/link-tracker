package ru.tbank.bot.command;

import ru.tbank.bot.model.IncomingMessage;
import ru.tbank.bot.repository.DialogRepository;

public class CancelCommand implements Command {

    public static final String NOTHING_TO_CANCEL_MESSAGE = "Сейчас нечего отменять.";

    private final DialogRepository dialogRepository;

    public CancelCommand(DialogRepository dialogRepository) {
        this.dialogRepository = dialogRepository;
    }

    @Override
    public String name() {
        return "/cancel";
    }

    @Override
    public String description() {
        return "отменить добавление ссылки";
    }

    @Override
    public String handle(IncomingMessage message) {
        if (dialogRepository.remove(message.chatId()) == null) {
            return NOTHING_TO_CANCEL_MESSAGE;
        }
        return Messages.TRACK_CANCELLED;
    }
}
