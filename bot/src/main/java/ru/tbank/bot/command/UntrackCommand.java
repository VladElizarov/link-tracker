package ru.tbank.bot.command;

import org.springframework.web.client.HttpClientErrorException;
import ru.tbank.bot.client.ScrapperClient;
import ru.tbank.bot.model.IncomingMessage;
import ru.tbank.bot.util.TextUtils;
import ru.tbank.common.dto.RemoveLinkRequest;

public class UntrackCommand implements Command {

    public static final String USAGE_MESSAGE =
        "Укажите ссылку: /untrack https://github.com/owner/repo";
    public static final String NOT_FOUND_MESSAGE =
        "Эта ссылка не отслеживается. Посмотреть свои ссылки: /list";

    private final ScrapperClient scrapperClient;

    public UntrackCommand(ScrapperClient scrapperClient) {
        this.scrapperClient = scrapperClient;
    }

    @Override
    public String name() {
        return "/untrack";
    }

    @Override
    public String description() {
        return "прекратить отслеживание ссылки";
    }

    @Override
    public String handle(IncomingMessage message) {
        String link = TextUtils.argumentsOf(message.text());
        if (link.isEmpty()) {
            return USAGE_MESSAGE;
        }

        try {
            scrapperClient.removeLink(message.chatId(), new RemoveLinkRequest(link));
            return "Ссылка " + link + " больше не отслеживается.";
        } catch (HttpClientErrorException.NotFound e) {
            return NOT_FOUND_MESSAGE;
        }
    }
}
