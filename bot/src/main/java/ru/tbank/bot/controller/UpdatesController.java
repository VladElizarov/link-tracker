package ru.tbank.bot.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.tbank.bot.service.LinkUpdateHandler;
import ru.tbank.common.dto.LinkUpdate;

@RestController
public class UpdatesController {

    private static final Logger log = LoggerFactory.getLogger(UpdatesController.class);

    private final LinkUpdateHandler linkUpdateHandler;

    public UpdatesController(LinkUpdateHandler linkUpdateHandler) {
        this.linkUpdateHandler = linkUpdateHandler;
    }

    @PostMapping("/updates")
    public void sendUpdate(@RequestBody @Valid LinkUpdate update) {
        log.atInfo()
            .addKeyValue("linkId", update.id())
            .addKeyValue("url", update.url())
            .addKeyValue("chatIds", update.tgChatIds())
            .log("Получено обновление от scrapper по HTTP");

        linkUpdateHandler.handle(update, null);
    }
}
