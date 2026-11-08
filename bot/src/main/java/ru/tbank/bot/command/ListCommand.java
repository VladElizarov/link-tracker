package ru.tbank.bot.command;

import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.HttpClientErrorException;
import ru.tbank.bot.client.ScrapperClient;
import ru.tbank.bot.model.IncomingMessage;
import ru.tbank.bot.util.TextUtils;
import ru.tbank.common.dto.LinkResponse;
import ru.tbank.common.dto.ListLinksResponse;

public class ListCommand implements Command {

    public static final String EMPTY_LIST_MESSAGE =
        "Список отслеживаемых ссылок пуст. Добавьте ссылку командой /track.";

    private final ScrapperClient scrapperClient;

    public ListCommand(ScrapperClient scrapperClient) {
        this.scrapperClient = scrapperClient;
    }

    @Override
    public String name() {
        return "/list";
    }

    @Override
    public String description() {
        return "список отслеживаемых ссылок (можно указать тег: /list работа)";
    }

    @Override
    public String handle(IncomingMessage message) {
        String tag = TextUtils.argumentsOf(message.text());

        ListLinksResponse response;
        try {
            response = scrapperClient.getLinks(message.chatId());
        } catch (HttpClientErrorException.NotFound e) {
            return Messages.CHAT_NOT_REGISTERED;
        }

        List<LinkResponse> links = new ArrayList<>();
        if (response != null && response.links() != null) {
            for (LinkResponse link : response.links()) {
                if (tag.isEmpty() || hasTag(link, tag)) {
                    links.add(link);
                }
            }
        }

        if (links.isEmpty()) {
            if (tag.isEmpty()) {
                return EMPTY_LIST_MESSAGE;
            }
            return "Нет отслеживаемых ссылок с тегом «" + tag + "».";
        }

        StringBuilder text = new StringBuilder();
        if (tag.isEmpty()) {
            text.append("Отслеживаемые ссылки:\n");
        } else {
            text.append("Отслеживаемые ссылки с тегом «").append(tag).append("»:\n");
        }
        for (int i = 0; i < links.size(); i++) {
            LinkResponse link = links.get(i);
            text.append(i + 1).append(". ").append(link.url());
            if (link.tags() != null && !link.tags().isEmpty()) {
                text.append(" (теги: ").append(String.join(", ", link.tags())).append(")");
            }
            text.append("\n");
        }
        return text.toString().trim();
    }

    private boolean hasTag(LinkResponse link, String tag) {
        if (link.tags() == null) {
            return false;
        }
        for (String linkTag : link.tags()) {
            if (linkTag.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }
}
