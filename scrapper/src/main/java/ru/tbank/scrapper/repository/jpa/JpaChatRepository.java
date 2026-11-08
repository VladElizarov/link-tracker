package ru.tbank.scrapper.repository.jpa;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.tbank.scrapper.repository.ChatRepository;
import ru.tbank.scrapper.repository.jpa.entity.ChatEntity;
import ru.tbank.scrapper.repository.jpa.springdata.SpringDataChatRepository;

@Repository
@Transactional
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "ORM")
public class JpaChatRepository implements ChatRepository {

    private final SpringDataChatRepository chatRepository;

    public JpaChatRepository(SpringDataChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    @Override
    public void save(long chatId) {
        chatRepository.save(new ChatEntity(chatId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(long chatId) {
        return chatRepository.existsById(chatId);
    }

    @Override
    public void delete(long chatId) {
        chatRepository.deleteById(chatId);
    }
}
