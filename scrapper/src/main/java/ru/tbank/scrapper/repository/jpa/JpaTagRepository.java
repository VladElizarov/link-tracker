package ru.tbank.scrapper.repository.jpa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.tbank.scrapper.repository.TagRepository;
import ru.tbank.scrapper.repository.jpa.entity.SubscriptionEntity;
import ru.tbank.scrapper.repository.jpa.entity.TagEntity;
import ru.tbank.scrapper.repository.jpa.springdata.SpringDataSubscriptionRepository;
import ru.tbank.scrapper.repository.jpa.springdata.SpringDataTagRepository;

@Repository
@Transactional
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "ORM")
public class JpaTagRepository implements TagRepository {

    private final SpringDataTagRepository tagRepository;
    private final SpringDataSubscriptionRepository subscriptionRepository;

    public JpaTagRepository(
        SpringDataTagRepository tagRepository,
        SpringDataSubscriptionRepository subscriptionRepository
    ) {
        this.tagRepository = tagRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findNamesByChatId(long chatId) {
        List<String> names = new ArrayList<>();
        for (TagEntity tag : tagRepository.findByChatIdOrderByNameAsc(chatId)) {
            names.add(tag.getName());
        }
        Collections.sort(names);
        return names;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(long chatId, String name) {
        return tagRepository.existsByChatIdAndName(chatId, name);
    }

    @Override
    public void create(long chatId, String name) {
        tagRepository.saveAndFlush(new TagEntity(chatId, name));
    }

    @Override
    public boolean rename(long chatId, String oldName, String newName) {
        Optional<TagEntity> tag = tagRepository.findByChatIdAndName(chatId, oldName);
        if (tag.isEmpty()) {
            return false;
        }
        tag.get().setName(newName);
        tagRepository.saveAndFlush(tag.get());
        return true;
    }

    @Override
    public boolean delete(long chatId, String name) {
        Optional<TagEntity> tag = tagRepository.findByChatIdAndName(chatId, name);
        if (tag.isEmpty()) {
            return false;
        }
        tagRepository.delete(tag.get());
        return true;
    }

    @Override
    public boolean addToSubscription(long chatId, String url, String name) {
        Optional<SubscriptionEntity> subscription = subscriptionRepository.findByChat_IdAndLink_Url(chatId, url);
        if (subscription.isEmpty()) {
            return false;
        }
        subscription.get().getTags().add(getOrCreateTag(chatId, name));
        return true;
    }

    @Override
    public boolean removeFromSubscription(long chatId, String url, String name) {
        Optional<SubscriptionEntity> subscription = subscriptionRepository.findByChat_IdAndLink_Url(chatId, url);
        if (subscription.isEmpty()) {
            return false;
        }
        return subscription.get().getTags().removeIf(tag -> tag.getName().equals(name));
    }

    public TagEntity getOrCreateTag(long chatId, String name) {
        Optional<TagEntity> existing = tagRepository.findByChatIdAndName(chatId, name);
        if (existing.isPresent()) {
            return existing.get();
        }
        return tagRepository.save(new TagEntity(chatId, name));
    }
}
