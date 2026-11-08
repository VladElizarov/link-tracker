package ru.tbank.scrapper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tbank.scrapper.cache.LinkListCacheInvalidator;
import ru.tbank.scrapper.exception.ChatAlreadyExistsException;
import ru.tbank.scrapper.exception.ChatNotFoundException;
import ru.tbank.scrapper.repository.ChatRepository;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatRepository chatRepository;
    private final LinkListCacheInvalidator cacheInvalidator;

    public ChatService(ChatRepository chatRepository, LinkListCacheInvalidator cacheInvalidator) {
        this.chatRepository = chatRepository;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Transactional
    public void register(long chatId) {
        if (chatRepository.exists(chatId)) {
            throw new ChatAlreadyExistsException(chatId);
        }
        chatRepository.save(chatId);
        log.atInfo().addKeyValue("chatId", chatId).log("Чат зарегистрирован");
    }

    @Transactional
    public void delete(long chatId) {
        if (!chatRepository.exists(chatId)) {
            throw new ChatNotFoundException(chatId);
        }
        chatRepository.delete(chatId);
        cacheInvalidator.evictAfterCommit(chatId);
        log.atInfo().addKeyValue("chatId", chatId).log("Чат удалён");
    }

    @Transactional(readOnly = true)
    public void checkExists(long chatId) {
        if (!chatRepository.exists(chatId)) {
            throw new ChatNotFoundException(chatId);
        }
    }
}
