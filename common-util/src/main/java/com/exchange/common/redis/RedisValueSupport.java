package com.exchange.common.redis;

import java.time.Duration;

public interface RedisValueSupport<T> extends BaseCacheReadSupport<T> {
    void set(String key, T value);

    void set(String key, T value, Duration ttl);

    Boolean setIfAbsent(String key, T value, Duration ttl);

    Boolean delete(String key);
}
