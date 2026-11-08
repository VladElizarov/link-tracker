package ru.tbank.scrapper.repository.jdbc;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.tbank.scrapper.repository.TagRepository;

@Repository
@Transactional
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "SQL", matchIfMissing = true)
public class JdbcTagRepository implements TagRepository {

    private final JdbcClient jdbcClient;

    public JdbcTagRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findNamesByChatId(long chatId) {
        return jdbcClient.sql("SELECT name FROM tags WHERE chat_id = :chatId ORDER BY name COLLATE \"C\"")
            .param("chatId", chatId)
            .query(String.class)
            .list();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(long chatId, String name) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM tags WHERE chat_id = :chatId AND name = :name)")
            .param("chatId", chatId)
            .param("name", name)
            .query(Boolean.class)
            .single();
    }

    @Override
    public void create(long chatId, String name) {
        jdbcClient.sql("INSERT INTO tags (chat_id, name) VALUES (:chatId, :name)")
            .param("chatId", chatId)
            .param("name", name)
            .update();
    }

    @Override
    public boolean rename(long chatId, String oldName, String newName) {
        int updated = jdbcClient.sql("UPDATE tags SET name = :newName WHERE chat_id = :chatId AND name = :oldName")
            .param("chatId", chatId)
            .param("oldName", oldName)
            .param("newName", newName)
            .update();
        return updated > 0;
    }

    @Override
    public boolean delete(long chatId, String name) {
        int deleted = jdbcClient.sql("DELETE FROM tags WHERE chat_id = :chatId AND name = :name")
            .param("chatId", chatId)
            .param("name", name)
            .update();
        return deleted > 0;
    }

    @Override
    public boolean addToSubscription(long chatId, String url, String name) {
        Optional<Long> subscriptionId = findSubscriptionId(chatId, url);
        if (subscriptionId.isEmpty()) {
            return false;
        }
        long tagId = getOrCreateTagId(chatId, name);
        jdbcClient.sql("""
                INSERT INTO subscription_tags (subscription_id, tag_id)
                VALUES (:subscriptionId, :tagId)
                ON CONFLICT DO NOTHING
                """)
            .param("subscriptionId", subscriptionId.get())
            .param("tagId", tagId)
            .update();
        return true;
    }

    @Override
    public boolean removeFromSubscription(long chatId, String url, String name) {
        int deleted = jdbcClient.sql("""
                DELETE FROM subscription_tags st
                USING subscriptions s, links l, tags t
                WHERE st.subscription_id = s.id
                  AND st.tag_id = t.id
                  AND s.link_id = l.id
                  AND s.chat_id = :chatId
                  AND l.url = :url
                  AND t.name = :name
                """)
            .param("chatId", chatId)
            .param("url", url)
            .param("name", name)
            .update();
        return deleted > 0;
    }

    public long getOrCreateTagId(long chatId, String name) {
        jdbcClient.sql("INSERT INTO tags (chat_id, name) VALUES (:chatId, :name) ON CONFLICT DO NOTHING")
            .param("chatId", chatId)
            .param("name", name)
            .update();
        return jdbcClient.sql("SELECT id FROM tags WHERE chat_id = :chatId AND name = :name")
            .param("chatId", chatId)
            .param("name", name)
            .query(Long.class)
            .single();
    }

    private Optional<Long> findSubscriptionId(long chatId, String url) {
        return jdbcClient.sql("""
                SELECT s.id
                FROM subscriptions s
                JOIN links l ON l.id = s.link_id
                WHERE s.chat_id = :chatId AND l.url = :url
                """)
            .param("chatId", chatId)
            .param("url", url)
            .query(Long.class)
            .optional();
    }
}
