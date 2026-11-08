package ru.tbank.scrapper.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.tbank.scrapper.config.CacheConfig;

@Component
@ConditionalOnProperty(prefix = "app.cache", name = "mode", havingValue = "VALKEY", matchIfMissing = true)
public class ValkeyLinkListCache extends AbstractValkeyLinkListCache {

    public ValkeyLinkListCache(
        StatefulRedisConnection<String, String> connection,
        ObjectMapper objectMapper,
        CacheConfig config
    ) {
        super(connection, objectMapper, config);
    }

    @Override
    protected String readJson(String key) {
        return connection.sync().get(key);
    }
}
