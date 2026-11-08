package ru.tbank.bot.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import ru.tbank.common.dto.AddLinkRequest;
import ru.tbank.common.dto.LinkResponse;
import ru.tbank.common.dto.ListLinksResponse;
import ru.tbank.common.dto.RemoveLinkRequest;

@HttpExchange
public interface ScrapperClient {

    String CHAT_ID_HEADER = "Tg-Chat-Id";

    @PostExchange("/tg-chat/{id}")
    void registerChat(@PathVariable long id);

    @DeleteExchange("/tg-chat/{id}")
    void deleteChat(@PathVariable long id);

    @GetExchange("/links")
    ListLinksResponse getLinks(@RequestHeader(CHAT_ID_HEADER) long chatId);

    @PostExchange("/links")
    LinkResponse addLink(@RequestHeader(CHAT_ID_HEADER) long chatId, @RequestBody AddLinkRequest request);

    @DeleteExchange("/links")
    LinkResponse removeLink(@RequestHeader(CHAT_ID_HEADER) long chatId, @RequestBody RemoveLinkRequest request);
}
