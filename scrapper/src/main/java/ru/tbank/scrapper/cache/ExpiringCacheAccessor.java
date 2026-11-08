package ru.tbank.scrapper.cache;

import io.lettuce.core.support.caching.CacheAccessor;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExpiringCacheAccessor implements CacheAccessor<String, String> {

    private record Entry(String value, long expiresAt) {
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public ExpiringCacheAccessor(Duration ttl) {
        this.ttlMillis = ttl.toMillis();
    }

    @Override
    public String get(String key) {
        Entry entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAt()) {
            map.remove(key);
            return null;
        }
        return entry.value();
    }

    @Override
    public void put(String key, String value) {
        map.put(key, new Entry(value, System.currentTimeMillis() + ttlMillis));
    }

    @Override
    public void evict(String key) {
        map.remove(key);
    }

    public void clear() {
        map.clear();
    }

    public int size() {
        return map.size();
    }
}
