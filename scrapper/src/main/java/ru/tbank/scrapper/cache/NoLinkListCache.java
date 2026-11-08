package ru.tbank.scrapper.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.tbank.common.dto.ListLinksResponse;

@Component
@ConditionalOnProperty(prefix = "app.cache", name = "mode", havingValue = "NONE")
public class NoLinkListCache implements LinkListCache {

    @Override
    public ListLinksResponse get(long chatId) {
        return null;
    }

    @Override
    public void put(long chatId, ListLinksResponse response) {
    }

    @Override
    public void evict(long chatId) {
    }
}
