package ru.tbank.scrapper.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tbank.common.dto.ListLinksResponse;
import ru.tbank.scrapper.config.CacheConfig;

public abstract class AbstractValkeyLinkListCache implements LinkListCache {

    private static final Logger log = LoggerFactory.getLogger(AbstractValkeyLinkListCache.class);

    protected final StatefulRedisConnection<String, String> connection;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    protected AbstractValkeyLinkListCache(
        StatefulRedisConnection<String, String> connection,
        ObjectMapper objectMapper,
        CacheConfig config
    ) {
        this.connection = connection;
        this.objectMapper = objectMapper;
        this.ttlSeconds = Math.max(1, config.ttl().toSeconds());
    }

    protected abstract String readJson(String key);

    public static String key(long chatId) {
        return String.valueOf(chatId);
    }

    @Override
    public ListLinksResponse get(long chatId) {
        try {
            String json = readJson(key(chatId));
            return json == null ? null : objectMapper.readValue(json, ListLinksResponse.class);
        } catch (Exception e) {
            log.atWarn().addKeyValue("chatId", chatId).addKeyValue("error", e.getMessage())
                .log("Не удалось прочитать кэш");
            return null;
        }
    }

    @Override
    public void put(long chatId, ListLinksResponse response) {
        try {
            connection.sync().set(key(chatId), toJson(response), SetArgs.Builder.ex(ttlSeconds));
        } catch (Exception e) {
            log.atWarn().addKeyValue("chatId", chatId).addKeyValue("error", e.getMessage())
                .log("Не удалось записать в кэш");
        }
    }

    @Override
    public void evict(long chatId) {
        try {
            connection.sync().del(key(chatId));
        } catch (Exception e) {
            log.atWarn().addKeyValue("chatId", chatId).addKeyValue("error", e.getMessage())
                .log("Не удалось удалить запись из кэша");
        }
    }

    private String toJson(ListLinksResponse response) throws JsonProcessingException {
        return objectMapper.writeValueAsString(response);
    }
}
