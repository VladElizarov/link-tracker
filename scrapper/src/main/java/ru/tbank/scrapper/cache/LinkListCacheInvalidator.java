package ru.tbank.scrapper.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class LinkListCacheInvalidator {

    private final LinkListCache cache;

    public LinkListCacheInvalidator(LinkListCache cache) {
        this.cache = cache;
    }

    public void evictAfterCommit(long chatId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.evict(chatId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.evict(chatId);
            }
        });
    }
}
