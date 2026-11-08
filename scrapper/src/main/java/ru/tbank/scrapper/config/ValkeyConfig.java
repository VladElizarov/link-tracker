package ru.tbank.scrapper.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.protocol.ProtocolVersion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("'${app.cache.mode:VALKEY}' != 'NONE'")
public class ValkeyConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient(ValkeyProperties properties) {
        RedisClient client = RedisClient.create(createUri(properties));
        client.setOptions(ClientOptions.builder()
            .protocolVersion(ProtocolVersion.RESP3)
            .timeoutOptions(TimeoutOptions.enabled(properties.timeout()))
            .build());
        return client;
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, String> valkeyConnection(RedisClient redisClient) {
        return redisClient.connect();
    }

    static RedisURI createUri(ValkeyProperties properties) {
        if (properties.sentinelMaster() == null || properties.sentinelMaster().isBlank()) {
            RedisURI uri = RedisURI.create(properties.url());
            uri.setTimeout(properties.timeout());
            return uri;
        }

        RedisURI.Builder builder = RedisURI.builder()
            .withSentinelMasterId(properties.sentinelMaster())
            .withTimeout(properties.timeout());
        for (String node : properties.sentinelNodes()) {
            String[] hostAndPort = node.trim().split(":");
            builder.withSentinel(hostAndPort[0], Integer.parseInt(hostAndPort[1]));
        }
        return builder.build();
    }
}
