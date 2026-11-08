package ru.tbank.scrapper.repository.jdbc;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.tbank.scrapper.model.Link;
import ru.tbank.scrapper.model.TrackedLink;
import ru.tbank.scrapper.repository.LinkRepository;

@Repository
@Transactional
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "SQL", matchIfMissing = true)
public class JdbcLinkRepository implements LinkRepository {

    private static final String SELECT_SUBSCRIPTIONS = """
        SELECT l.id AS link_id,
               l.url,
               ARRAY(SELECT t.name
                     FROM subscription_tags st
                     JOIN tags t ON t.id = st.tag_id
                     WHERE st.subscription_id = s.id
                     ORDER BY t.name COLLATE "C") AS tags,
               ARRAY(SELECT f.filter_value
                     FROM subscription_filters f
                     WHERE f.subscription_id = s.id
                     ORDER BY f.filter_value COLLATE "C") AS filters
        FROM subscriptions s
        JOIN links l ON l.id = s.link_id
        """;

    private final JdbcClient jdbcClient;
    private final JdbcTagRepository tagRepository;

    public JdbcLinkRepository(JdbcClient jdbcClient, JdbcTagRepository tagRepository) {
        this.jdbcClient = jdbcClient;
        this.tagRepository = tagRepository;
    }

    @Override
    public TrackedLink addSubscription(long chatId, String url, List<String> tags, List<String> filters) {
        jdbcClient.sql("INSERT INTO links (url) VALUES (:url) ON CONFLICT (url) DO NOTHING")
            .param("url", url)
            .update();
        long linkId = jdbcClient.sql("SELECT id FROM links WHERE url = :url")
            .param("url", url)
            .query(Long.class)
            .single();

        long subscriptionId = jdbcClient.sql("""
                INSERT INTO subscriptions (chat_id, link_id)
                VALUES (:chatId, :linkId)
                RETURNING id
                """)
            .param("chatId", chatId)
            .param("linkId", linkId)
            .query(Long.class)
            .single();

        for (String tag : tags) {
            long tagId = tagRepository.getOrCreateTagId(chatId, tag);
            jdbcClient.sql("""
                    INSERT INTO subscription_tags (subscription_id, tag_id)
                    VALUES (:subscriptionId, :tagId)
                    ON CONFLICT DO NOTHING
                    """)
                .param("subscriptionId", subscriptionId)
                .param("tagId", tagId)
                .update();
        }

        for (String filter : filters) {
            jdbcClient.sql("""
                    INSERT INTO subscription_filters (subscription_id, filter_value)
                    VALUES (:subscriptionId, :filter)
                    ON CONFLICT DO NOTHING
                    """)
                .param("subscriptionId", subscriptionId)
                .param("filter", filter)
                .update();
        }

        return findSubscription(chatId, url).orElseThrow();
    }

    @Override
    public TrackedLink removeSubscription(long chatId, String url) {
        Optional<TrackedLink> subscription = findSubscription(chatId, url);
        if (subscription.isEmpty()) {
            return null;
        }
        jdbcClient.sql("DELETE FROM subscriptions WHERE chat_id = :chatId AND link_id = :linkId")
            .param("chatId", chatId)
            .param("linkId", subscription.get().id())
            .update();
        return subscription.get();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsSubscription(long chatId, String url) {
        return jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM subscriptions s
                    JOIN links l ON l.id = s.link_id
                    WHERE s.chat_id = :chatId AND l.url = :url
                )
                """)
            .param("chatId", chatId)
            .param("url", url)
            .query(Boolean.class)
            .single();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackedLink> findSubscriptionsByChatId(long chatId) {
        return jdbcClient.sql(SELECT_SUBSCRIPTIONS + " WHERE s.chat_id = :chatId ORDER BY s.id")
            .param("chatId", chatId)
            .query((rs, rowNum) -> mapTrackedLink(rs))
            .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Link> findLinksToCheck(Instant checkedBefore, long afterId, int limit) {
        return jdbcClient.sql("""
                SELECT l.id, l.url, l.last_update
                FROM links l
                WHERE (l.last_checked_at IS NULL OR l.last_checked_at < :checkedBefore)
                  AND l.id > :afterId
                  AND EXISTS (SELECT 1 FROM subscriptions s WHERE s.link_id = l.id)
                ORDER BY l.id
                LIMIT :limit
                """)
            .param("checkedBefore", toDb(checkedBefore))
            .param("afterId", afterId)
            .param("limit", limit)
            .query((rs, rowNum) -> new Link(
                rs.getLong("id"),
                rs.getString("url"),
                fromDb(rs.getObject("last_update", OffsetDateTime.class))
            ))
            .list();
    }

    @Override
    public void updateCheckResult(long linkId, Instant lastUpdate, Instant checkedAt) {
        jdbcClient.sql("UPDATE links SET last_update = :lastUpdate, last_checked_at = :checkedAt WHERE id = :id")
            .param("id", linkId)
            .param("lastUpdate", toDb(lastUpdate))
            .param("checkedAt", toDb(checkedAt))
            .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findChatIdsByLinkId(long linkId) {
        return jdbcClient.sql("SELECT chat_id FROM subscriptions WHERE link_id = :linkId ORDER BY chat_id")
            .param("linkId", linkId)
            .query(Long.class)
            .list();
    }

    private Optional<TrackedLink> findSubscription(long chatId, String url) {
        return jdbcClient.sql(SELECT_SUBSCRIPTIONS + " WHERE s.chat_id = :chatId AND l.url = :url")
            .param("chatId", chatId)
            .param("url", url)
            .query((rs, rowNum) -> mapTrackedLink(rs))
            .optional();
    }

    private TrackedLink mapTrackedLink(ResultSet rs) throws SQLException {
        return new TrackedLink(
            rs.getLong("link_id"),
            rs.getString("url"),
            toList(rs.getArray("tags")),
            toList(rs.getArray("filters"))
        );
    }

    private List<String> toList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        String[] values = (String[]) array.getArray();
        return new ArrayList<>(Arrays.asList(values));
    }

    private OffsetDateTime toDb(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private Instant fromDb(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }
}
