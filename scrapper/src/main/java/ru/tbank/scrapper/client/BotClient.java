package ru.tbank.scrapper.client;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import ru.tbank.common.dto.LinkUpdate;

@HttpExchange
public interface BotClient {

    @PostExchange("/updates")
    void sendUpdate(@RequestBody LinkUpdate update);
}
