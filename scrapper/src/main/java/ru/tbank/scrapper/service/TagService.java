package ru.tbank.scrapper.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tbank.scrapper.cache.LinkListCacheInvalidator;
import ru.tbank.scrapper.exception.LinkNotFoundException;
import ru.tbank.scrapper.exception.TagAlreadyExistsException;
import ru.tbank.scrapper.exception.TagNotFoundException;
import ru.tbank.scrapper.repository.TagRepository;

@Service
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tagRepository;
    private final ChatService chatService;
    private final LinkListCacheInvalidator cacheInvalidator;

    public TagService(
        TagRepository tagRepository,
        ChatService chatService,
        LinkListCacheInvalidator cacheInvalidator
    ) {
        this.tagRepository = tagRepository;
        this.chatService = chatService;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Transactional(readOnly = true)
    public List<String> getTags(long chatId) {
        chatService.checkExists(chatId);
        return tagRepository.findNamesByChatId(chatId);
    }

    @Transactional
    public void create(long chatId, String name) {
        chatService.checkExists(chatId);
        name = name.trim();
        if (tagRepository.exists(chatId, name)) {
            throw new TagAlreadyExistsException(name);
        }
        tagRepository.create(chatId, name);
        log.atInfo().addKeyValue("chatId", chatId).addKeyValue("tag", name).log("Тег создан");
    }

    @Transactional
    public void rename(long chatId, String oldName, String newName) {
        chatService.checkExists(chatId);
        newName = newName.trim();
        if (!tagRepository.exists(chatId, oldName)) {
            throw new TagNotFoundException(oldName);
        }
        if (tagRepository.exists(chatId, newName)) {
            throw new TagAlreadyExistsException(newName);
        }
        tagRepository.rename(chatId, oldName, newName);
        cacheInvalidator.evictAfterCommit(chatId);
        log.atInfo()
            .addKeyValue("chatId", chatId)
            .addKeyValue("oldName", oldName)
            .addKeyValue("newName", newName)
            .log("Тег переименован");
    }

    @Transactional
    public void delete(long chatId, String name) {
        chatService.checkExists(chatId);
        if (!tagRepository.delete(chatId, name)) {
            throw new TagNotFoundException(name);
        }
        cacheInvalidator.evictAfterCommit(chatId);
        log.atInfo().addKeyValue("chatId", chatId).addKeyValue("tag", name).log("Тег удалён");
    }

    @Transactional
    public void addTagToLink(long chatId, String url, String name) {
        chatService.checkExists(chatId);
        if (!tagRepository.addToSubscription(chatId, url.trim(), name.trim())) {
            throw new LinkNotFoundException(url);
        }
        cacheInvalidator.evictAfterCommit(chatId);
    }

    @Transactional
    public void removeTagFromLink(long chatId, String url, String name) {
        chatService.checkExists(chatId);
        if (!tagRepository.removeFromSubscription(chatId, url.trim(), name.trim())) {
            throw new TagNotFoundException(name);
        }
        cacheInvalidator.evictAfterCommit(chatId);
    }
}
