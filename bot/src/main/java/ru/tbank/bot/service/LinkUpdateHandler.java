package ru.tbank.bot.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.tbank.common.dto.LinkUpdate;

@Service
public class LinkUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(LinkUpdateHandler.class);

    private static final int MAX_REMEMBERED = 10_000;

    private final TelegramBotService telegramBotService;

    private final Set<String> alreadySent = Collections.synchronizedSet(new LinkedHashSet<>());

    public LinkUpdateHandler(TelegramBotService telegramBotService) {
        this.telegramBotService = telegramBotService;
    }

    public void handle(LinkUpdate update, String messageId) {
        String text = "Обновление по ссылке " + update.url();
        if (update.description() != null && !update.description().isBlank()) {
            text = text + "\n" + update.description();
        }

        for (Long chatId : update.tgChatIds()) {
            String key = messageId + ":" + chatId;
            if (messageId != null && alreadySent.contains(key)) {
                log.atInfo()
                    .addKeyValue("messageId", messageId)
                    .addKeyValue("chatId", chatId)
                    .log("Повторное сообщение, уже отправлено - пропускаем");
                continue;
            }
            telegramBotService.sendNotification(chatId, text);
            if (messageId != null) {
                remember(key);
            }
        }
    }

    private void remember(String key) {
        synchronized (alreadySent) {
            alreadySent.add(key);
            if (alreadySent.size() > MAX_REMEMBERED) {
                alreadySent.remove(alreadySent.iterator().next());
            }
        }
    }
}
