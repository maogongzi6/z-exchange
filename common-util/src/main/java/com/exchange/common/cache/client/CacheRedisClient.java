package com.exchange.common.cache.client;

import java.time.Duration;

public class CacheRedisClient<T> {
    final private BaseRedisSupport<T> baseRedisSupport;

    public CacheRedisClient(BaseRedisSupport<T> baseRedisSupport) {
        this.baseRedisSupport = baseRedisSupport;
    }

    protected void set(String key, T value) {
        baseRedisSupport.set(key, value);
    }

    protected void set(String key, T value, Duration ttl) {
        baseRedisSupport.set(key, value, ttl);
    }

    protected T get(String key) {
        return baseRedisSupport.get(key);
    }

    protected Boolean setIfAbsent(String key, T value, Duration ttl) {
        return baseRedisSupport.setIfAbsent(key, value, ttl);
    }

    protected Boolean delete(String key) {
        return baseRedisSupport.delete(key);
    }
}
