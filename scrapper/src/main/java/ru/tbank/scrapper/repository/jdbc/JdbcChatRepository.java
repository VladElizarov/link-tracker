package ru.tbank.scrapper.repository.jdbc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.tbank.scrapper.repository.ChatRepository;

@Repository
@Transactional
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "SQL", matchIfMissing = true)
public class JdbcChatRepository implements ChatRepository {

    private final JdbcClient jdbcClient;

    public JdbcChatRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void save(long chatId) {
        jdbcClient.sql("INSERT INTO chats (id) VALUES (:id)")
            .param("id", chatId)
            .update();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(long chatId) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM chats WHERE id = :id)")
            .param("id", chatId)
            .query(Boolean.class)
            .single();
    }

    @Override
    public void delete(long chatId) {
        jdbcClient.sql("DELETE FROM chats WHERE id = :id")
            .param("id", chatId)
            .update();
    }
}
