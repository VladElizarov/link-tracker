package ru.tbank.scrapper.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.TrackingArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.support.caching.CacheFrontend;
import io.lettuce.core.support.caching.ClientSideCaching;
import java.net.SocketAddress;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.tbank.scrapper.config.CacheConfig;

@Component
@ConditionalOnProperty(prefix = "app.cache", name = "mode", havingValue = "CLIENT_SIDE")
public class ClientSideLinkListCache extends AbstractValkeyLinkListCache {

    private static final Logger log = LoggerFactory.getLogger(ClientSideLinkListCache.class);

    private final ExpiringCacheAccessor localCopy;
    private final CacheFrontend<String, String> frontend;

    public ClientSideLinkListCache(
        RedisClient redisClient,
        StatefulRedisConnection<String, String> connection,
        ObjectMapper objectMapper,
        CacheConfig config
    ) {
        super(connection, objectMapper, config);
        this.localCopy = new ExpiringCacheAccessor(config.ttl());
        connection.addListener(message -> {
            if ("invalidate".equals(message.getType())) {
                List<Object> content = message.getContent();
                if (content.size() < 2 || content.get(1) == null) {
                    localCopy.clear();
                }
            }
        });
        this.frontend = ClientSideCaching.enable(localCopy, connection, TrackingArgs.Builder.enabled());
        redisClient.addListener(new ReconnectListener());
    }

    @Override
    protected String readJson(String key) {
        return frontend.get(key);
    }

    public int localSize() {
        return localCopy.size();
    }

    private class ReconnectListener implements RedisConnectionStateListener {

        @Override
        public void onRedisConnected(RedisChannelHandler<?, ?> handler, SocketAddress address) {
            if (handler != connection) {
                return;
            }
            localCopy.clear();
            connection.async().clientTracking(TrackingArgs.Builder.enabled());
            log.atInfo().addKeyValue("address", address).log("Переподключились к Valkey, отслеживание ключей включено снова");
        }

        @Override
        public void onRedisDisconnected(RedisChannelHandler<?, ?> handler) {
            if (handler == connection) {
                localCopy.clear();
            }
        }
    }
}
