package ru.tbank.scrapper.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tbank.scrapper.cache.LinkListCacheInvalidator;
import ru.tbank.common.dto.LinkResponse;
import ru.tbank.common.dto.ListLinksResponse;
import ru.tbank.scrapper.exception.LinkAlreadyExistsException;
import ru.tbank.scrapper.exception.LinkNotFoundException;
import ru.tbank.scrapper.exception.UnsupportedLinkException;
import ru.tbank.scrapper.link.LinkParser;
import ru.tbank.scrapper.model.TrackedLink;
import ru.tbank.scrapper.repository.LinkRepository;

@Service
public class LinkService {

    private static final Logger log = LoggerFactory.getLogger(LinkService.class);

    private final LinkRepository linkRepository;
    private final ChatService chatService;
    private final LinkParser linkParser;
    private final LinkListCacheInvalidator cacheInvalidator;

    public LinkService(
        LinkRepository linkRepository,
        ChatService chatService,
        LinkParser linkParser,
        LinkListCacheInvalidator cacheInvalidator
    ) {
        this.linkRepository = linkRepository;
        this.chatService = chatService;
        this.linkParser = linkParser;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Transactional
    public LinkResponse add(long chatId, String url, List<String> tags, List<String> filters) {
        chatService.checkExists(chatId);
        url = url.trim();

        if (linkParser.parse(url) == null) {
            throw new UnsupportedLinkException(url);
        }
        if (linkRepository.existsSubscription(chatId, url)) {
            throw new LinkAlreadyExistsException(url);
        }

        TrackedLink link = linkRepository.addSubscription(chatId, url, clean(tags), clean(filters));
        cacheInvalidator.evictAfterCommit(chatId);
        log.atInfo()
            .addKeyValue("chatId", chatId)
            .addKeyValue("url", url)
            .addKeyValue("tags", link.tags())
            .log("Ссылка добавлена");
        return toResponse(link);
    }

    @Transactional
    public LinkResponse remove(long chatId, String url) {
        chatService.checkExists(chatId);
        url = url.trim();

        TrackedLink removed = linkRepository.removeSubscription(chatId, url);
        if (removed == null) {
            throw new LinkNotFoundException(url);
        }
        cacheInvalidator.evictAfterCommit(chatId);
        log.atInfo()
            .addKeyValue("chatId", chatId)
            .addKeyValue("url", url)
            .log("Ссылка удалена");
        return toResponse(removed);
    }

    @Transactional(readOnly = true)
    public ListLinksResponse getAll(long chatId) {
        chatService.checkExists(chatId);

        List<LinkResponse> result = new ArrayList<>();
        for (TrackedLink link : linkRepository.findSubscriptionsByChatId(chatId)) {
            result.add(toResponse(link));
        }
        return new ListLinksResponse(result, result.size());
    }

    private LinkResponse toResponse(TrackedLink link) {
        return new LinkResponse(link.id(), link.url(), link.tags(), link.filters());
    }

    private List<String> clean(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    result.add(value.trim());
                }
            }
        }
        return new ArrayList<>(result);
    }
}
