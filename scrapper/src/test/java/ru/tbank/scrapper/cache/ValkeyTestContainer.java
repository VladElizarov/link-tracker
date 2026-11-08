package ru.tbank.scrapper.cache;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;

public final class ValkeyTestContainer {

    public static final GenericContainer<?> VALKEY =
        new GenericContainer<>("valkey/valkey:8.1.11").withExposedPorts(6379);

    static {
        VALKEY.start();
    }

    private ValkeyTestContainer() {
    }

    public static String url() {
        return "redis://" + VALKEY.getHost() + ":" + VALKEY.getMappedPort(6379);
    }

    public static void register(DynamicPropertyRegistry registry, String cacheMode) {
        registry.add("app.cache.mode", () -> cacheMode);
        registry.add("app.valkey.sentinel-master", () -> "");
        registry.add("app.valkey.url", ValkeyTestContainer::url);
    }

    public static RedisCommands<String, String> testClient() {
        return RedisClient.create(url()).connect().sync();
    }
}
