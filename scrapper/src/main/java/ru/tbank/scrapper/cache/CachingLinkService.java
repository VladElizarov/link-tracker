package ru.tbank.scrapper.cache;

import org.springframework.stereotype.Service;
import ru.tbank.common.dto.ListLinksResponse;
import ru.tbank.scrapper.service.LinkService;

@Service
public class CachingLinkService {

    private final LinkService linkService;
    private final LinkListCache cache;

    public CachingLinkService(LinkService linkService, LinkListCache cache) {
        this.linkService = linkService;
        this.cache = cache;
    }

    public ListLinksResponse getAll(long chatId) {
        ListLinksResponse cached = cache.get(chatId);
        if (cached != null) {
            return cached;
        }
        ListLinksResponse response = linkService.getAll(chatId);
        cache.put(chatId, response);
        return response;
    }
}
