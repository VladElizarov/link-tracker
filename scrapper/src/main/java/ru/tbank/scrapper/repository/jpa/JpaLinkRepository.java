package ru.tbank.scrapper.repository.jpa;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.tbank.scrapper.model.Link;
import ru.tbank.scrapper.model.TrackedLink;
import ru.tbank.scrapper.repository.LinkRepository;
import ru.tbank.scrapper.repository.jpa.entity.ChatEntity;
import ru.tbank.scrapper.repository.jpa.entity.LinkEntity;
import ru.tbank.scrapper.repository.jpa.entity.SubscriptionEntity;
import ru.tbank.scrapper.repository.jpa.entity.TagEntity;
import ru.tbank.scrapper.repository.jpa.springdata.SpringDataChatRepository;
import ru.tbank.scrapper.repository.jpa.springdata.SpringDataLinkRepository;
import ru.tbank.scrapper.repository.jpa.springdata.SpringDataSubscriptionRepository;

@Repository
@Transactional
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "ORM")
public class JpaLinkRepository implements LinkRepository {

    private final SpringDataChatRepository chatRepository;
    private final SpringDataLinkRepository linkRepository;
    private final SpringDataSubscriptionRepository subscriptionRepository;
    private final JpaTagRepository tagRepository;

    public JpaLinkRepository(
        SpringDataChatRepository chatRepository,
        SpringDataLinkRepository linkRepository,
        SpringDataSubscriptionRepository subscriptionRepository,
        JpaTagRepository tagRepository
    ) {
        this.chatRepository = chatRepository;
        this.linkRepository = linkRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tagRepository = tagRepository;
    }

    @Override
    public TrackedLink addSubscription(long chatId, String url, List<String> tags, List<String> filters) {
        ChatEntity chat = chatRepository.getReferenceById(chatId);

        LinkEntity link = linkRepository.findByUrl(url)
            .orElseGet(() -> linkRepository.save(new LinkEntity(url)));

        SubscriptionEntity subscription = new SubscriptionEntity(chat, link);
        for (String tagName : tags) {
            subscription.getTags().add(tagRepository.getOrCreateTag(chatId, tagName));
        }
        for (String filter : filters) {
            if (!subscription.getFilters().contains(filter)) {
                subscription.getFilters().add(filter);
            }
        }

        subscriptionRepository.saveAndFlush(subscription);
        return toTrackedLink(subscription);
    }

    @Override
    public TrackedLink removeSubscription(long chatId, String url) {
        Optional<SubscriptionEntity> subscription = subscriptionRepository.findByChat_IdAndLink_Url(chatId, url);
        if (subscription.isEmpty()) {
            return null;
        }
        TrackedLink removed = toTrackedLink(subscription.get());
        subscriptionRepository.delete(subscription.get());
        return removed;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsSubscription(long chatId, String url) {
        return subscriptionRepository.existsByChat_IdAndLink_Url(chatId, url);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackedLink> findSubscriptionsByChatId(long chatId) {
        List<TrackedLink> result = new ArrayList<>();
        for (SubscriptionEntity subscription : subscriptionRepository.findByChat_IdOrderByIdAsc(chatId)) {
            result.add(toTrackedLink(subscription));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Link> findLinksToCheck(Instant checkedBefore, long afterId, int limit) {
        List<Link> result = new ArrayList<>();
        for (LinkEntity link : linkRepository.findLinksToCheck(checkedBefore, afterId, PageRequest.of(0, limit))) {
            result.add(new Link(link.getId(), link.getUrl(), link.getLastUpdate()));
        }
        return result;
    }

    @Override
    public void updateCheckResult(long linkId, Instant lastUpdate, Instant checkedAt) {
        linkRepository.findById(linkId).ifPresent(link -> {
            link.setLastUpdate(lastUpdate);
            link.setLastCheckedAt(checkedAt);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findChatIdsByLinkId(long linkId) {
        return subscriptionRepository.findChatIdsByLinkId(linkId);
    }

    private TrackedLink toTrackedLink(SubscriptionEntity subscription) {
        List<String> tagNames = new ArrayList<>();
        for (TagEntity tag : subscription.getTags()) {
            tagNames.add(tag.getName());
        }
        List<String> filters = new ArrayList<>(subscription.getFilters());
        Collections.sort(tagNames);
        Collections.sort(filters);
        return new TrackedLink(subscription.getLink().getId(), subscription.getLink().getUrl(), tagNames, filters);
    }
}
