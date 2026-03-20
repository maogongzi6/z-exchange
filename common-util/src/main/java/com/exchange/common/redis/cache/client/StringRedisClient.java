package com.exchange.common.redis.cache.client;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.support.SimpleCacheSupport;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@RequiredArgsConstructor
public class StringRedisClient {
    final private BaseRedisSupport<String> baseRedisSupport;
    final private SimpleCacheSupport simpleCacheSupport;

    public Result<Void> set(String key, String value, Duration ttl) {
        return simpleCacheSupport.set(key, value, ttl);
    }

    public Result<CacheValueInfo<String>> get(String key) {
        return simpleCacheSupport.get(key);
    }

    public Boolean delete(String key) {
        return baseRedisSupport.delete(key);
    }
}
