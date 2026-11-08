package ru.tbank.scrapper.repository;

import java.time.Instant;
import java.util.List;
import ru.tbank.scrapper.model.Link;
import ru.tbank.scrapper.model.TrackedLink;

public interface LinkRepository {

    TrackedLink addSubscription(long chatId, String url, List<String> tags, List<String> filters);

    TrackedLink removeSubscription(long chatId, String url);

    boolean existsSubscription(long chatId, String url);

    List<TrackedLink> findSubscriptionsByChatId(long chatId);

    List<Link> findLinksToCheck(Instant checkedBefore, long afterId, int limit);

    void updateCheckResult(long linkId, Instant lastUpdate, Instant checkedAt);

    List<Long> findChatIdsByLinkId(long linkId);
}
