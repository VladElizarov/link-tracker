package ru.tbank.bot.command;

import ru.tbank.bot.dialog.TrackDialog;
import ru.tbank.bot.model.IncomingMessage;
import ru.tbank.bot.repository.DialogRepository;

public class TrackCommand implements Command {

    public static final String ASK_LINK_MESSAGE =
        "Отправьте ссылку, которую нужно отслеживать (репозиторий GitHub или вопрос StackOverflow).\n"
            + "Чтобы отменить, отправьте /cancel.";

    private final DialogRepository dialogRepository;

    public TrackCommand(DialogRepository dialogRepository) {
        this.dialogRepository = dialogRepository;
    }

    @Override
    public String name() {
        return "/track";
    }

    @Override
    public String description() {
        return "начать отслеживание ссылки";
    }

    @Override
    public String handle(IncomingMessage message) {
        dialogRepository.save(message.chatId(), new TrackDialog());
        return ASK_LINK_MESSAGE;
    }
}
