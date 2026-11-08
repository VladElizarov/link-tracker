package ru.tbank.scrapper.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.model.OutboxMessage;
import ru.tbank.scrapper.repository.OutboxRepository;

@Repository
public class JdbcOutboxRepository implements OutboxRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcOutboxRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(LinkUpdate update) {
        jdbcClient.sql("INSERT INTO outbox (payload) VALUES (:payload)")
            .param("payload", toJson(update))
            .update();
    }

    @Override
    public List<OutboxMessage> findNew(int limit) {
        return jdbcClient.sql("SELECT id, payload FROM outbox WHERE status = 'NEW' ORDER BY id LIMIT :limit")
            .param("limit", limit)
            .query((rs, rowNum) -> new OutboxMessage(rs.getLong("id"), fromJson(rs.getString("payload"))))
            .list();
    }

    @Override
    public void markSent(long id) {
        jdbcClient.sql("UPDATE outbox SET status = 'SENT', sent_at = now() WHERE id = :id")
            .param("id", id)
            .update();
    }

    private String toJson(LinkUpdate update) {
        try {
            return objectMapper.writeValueAsString(update);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось превратить уведомление в JSON", e);
        }
    }

    private LinkUpdate fromJson(String json) {
        try {
            return objectMapper.readValue(json, LinkUpdate.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("В outbox лежит некорректный JSON: " + json, e);
        }
    }
}
