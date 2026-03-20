package com.exchange.common.redis.cache.client;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.support.CacheReadSupport;
import com.exchange.common.redis.cache.component.support.CacheWriteSupport;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@RequiredArgsConstructor
public class StringRedisClient {
    final private BaseRedisSupport<String> baseRedisSupport;
    final private CacheReadSupport cacheReadSupport;
    final private CacheWriteSupport cacheWriteSupport;

    public Result<Void> set(String key, String value, Duration ttl) {
        return cacheWriteSupport.set(key, value, ttl);
    }

    public Result<CacheValueInfo<String>> get(String key) {
        return cacheReadSupport.get(key);
    }

    public Boolean delete(String key) {
        return baseRedisSupport.delete(key);
    }
}
