package ru.tbank.scrapper.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.client.BotClient;

@Component
@ConditionalOnProperty(prefix = "app", name = "message-transport", havingValue = "HTTP")
public class HttpMessageSender implements MessageSender {

    private final BotClient botClient;

    public HttpMessageSender(BotClient botClient) {
        this.botClient = botClient;
    }

    @Override
    public void send(long messageId, LinkUpdate update) {
        botClient.sendUpdate(update);
    }
}
