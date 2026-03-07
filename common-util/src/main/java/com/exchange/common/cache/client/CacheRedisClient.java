package com.exchange.common.cache.client;

import java.time.Duration;

public class CacheRedisClient<T> {
    final private BaseRedisSupport<T> baseRedisSupport;

    public CacheRedisClient(BaseRedisSupport<T> baseRedisSupport) {
        this.baseRedisSupport = baseRedisSupport;
    }

    public void set(String key, T value, Duration ttl) {
        baseRedisSupport.set(key, value, ttl);
    }

    public T get(String key) {
        return baseRedisSupport.get(key);
    }

    public Boolean setIfAbsent(String key, T value, Duration ttl) {
        return baseRedisSupport.setIfAbsent(key, value, ttl);
    }

    public Boolean delete(String key) {
        return baseRedisSupport.delete(key);
    }
}
