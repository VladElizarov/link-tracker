package ru.tbank.bot.dialog;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import ru.tbank.bot.client.ScrapperClient;
import ru.tbank.bot.command.Messages;
import ru.tbank.bot.model.IncomingMessage;
import ru.tbank.bot.repository.DialogRepository;
import ru.tbank.bot.util.LinkValidator;
import ru.tbank.bot.util.TextUtils;
import ru.tbank.common.dto.AddLinkRequest;
import ru.tbank.common.dto.LinkResponse;
import ru.tbank.common.dto.ListLinksResponse;

@Component
public class TrackDialogHandler {

    public static final String ASK_TAGS_MESSAGE =
        "Введите теги через запятую (например: работа, баг) или «-», чтобы пропустить.";
    public static final String ASK_FILTERS_MESSAGE =
        "Введите фильтры через запятую (например: user:dummy) или «-», чтобы пропустить.";

    private static final Logger log = LoggerFactory.getLogger(TrackDialogHandler.class);

    private final DialogRepository dialogRepository;
    private final ScrapperClient scrapperClient;

    public TrackDialogHandler(DialogRepository dialogRepository, ScrapperClient scrapperClient) {
        this.dialogRepository = dialogRepository;
        this.scrapperClient = scrapperClient;
    }

    public String handle(IncomingMessage message, TrackDialog dialog) {
        log.atDebug()
            .addKeyValue("chatId", message.chatId())
            .addKeyValue("state", dialog.getState())
            .log("Шаг диалога /track");

        switch (dialog.getState()) {
            case WAITING_LINK:
                return handleLink(message, dialog);
            case WAITING_TAGS:
                return handleTags(message, dialog);
            case WAITING_FILTERS:
                return handleFilters(message, dialog);
            default:
                dialogRepository.remove(message.chatId());
                return Messages.TRACK_CANCELLED;
        }
    }

    private String handleLink(IncomingMessage message, TrackDialog dialog) {
        String link = message.text().trim();
        if (!LinkValidator.isValid(link)) {
            return Messages.INVALID_LINK;
        }

        try {
            if (isAlreadyTracked(message.chatId(), link)) {
                dialogRepository.remove(message.chatId());
                return Messages.LINK_ALREADY_TRACKED;
            }
        } catch (HttpClientErrorException.NotFound e) {
            dialogRepository.remove(message.chatId());
            return Messages.CHAT_NOT_REGISTERED;
        }

        dialog.setLink(link);
        dialog.setState(DialogState.WAITING_TAGS);
        return ASK_TAGS_MESSAGE;
    }

    private String handleTags(IncomingMessage message, TrackDialog dialog) {
        dialog.setTags(TextUtils.parseCommaSeparated(message.text()));
        dialog.setState(DialogState.WAITING_FILTERS);
        return ASK_FILTERS_MESSAGE;
    }

    private String handleFilters(IncomingMessage message, TrackDialog dialog) {
        List<String> filters = TextUtils.parseCommaSeparated(message.text());
        dialogRepository.remove(message.chatId());

        try {
            scrapperClient.addLink(message.chatId(), new AddLinkRequest(dialog.getLink(), dialog.getTags(), filters));
        } catch (HttpClientErrorException.Conflict e) {
            return Messages.LINK_ALREADY_TRACKED;
        } catch (HttpClientErrorException.NotFound e) {
            return Messages.CHAT_NOT_REGISTERED;
        } catch (HttpClientErrorException.BadRequest e) {
            return "Ссылка " + dialog.getLink() + " не поддерживается. Начните заново: /track";
        }

        StringBuilder text = new StringBuilder("Ссылка добавлена: ").append(dialog.getLink());
        if (!dialog.getTags().isEmpty()) {
            text.append("\nТеги: ").append(String.join(", ", dialog.getTags()));
        }
        if (!filters.isEmpty()) {
            text.append("\nФильтры: ").append(String.join(", ", filters));
        }
        return text.toString();
    }

    private boolean isAlreadyTracked(long chatId, String link) {
        ListLinksResponse response = scrapperClient.getLinks(chatId);
        if (response == null || response.links() == null) {
            return false;
        }
        for (LinkResponse existing : response.links()) {
            if (link.equals(existing.url())) {
                return true;
            }
        }
        return false;
    }
}
