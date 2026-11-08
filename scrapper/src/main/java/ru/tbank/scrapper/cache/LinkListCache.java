package ru.tbank.scrapper.cache;

import ru.tbank.common.dto.ListLinksResponse;

public interface LinkListCache {

    ListLinksResponse get(long chatId);

    void put(long chatId, ListLinksResponse response);

    void evict(long chatId);
}
