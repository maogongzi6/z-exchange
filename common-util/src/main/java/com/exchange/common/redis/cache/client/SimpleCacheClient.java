package com.exchange.common.redis.cache.client;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.support.SimpleRedisSupport;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SimpleCacheClient implements CacheReader {
    final private BaseRedisSupport<String> baseRedisSupport;
    final private SimpleRedisSupport simpleRedisSupport;

    public <T> Result<CacheValueInfo<T>> get(String key, Class<T> clazz) {
        return simpleRedisSupport.get(key, clazz);
    }

    public <T> Result<Void> set(String key, T value, TtlStrategy ttl) {
        return simpleRedisSupport.set(key, value, ttl.afterJitter());
    }

    public Result<Void> setNegative(String key, TtlStrategy ttl) {
        return simpleRedisSupport.setNegative(key, ttl.afterJitter());
    }

    public Boolean delete(String key) {
        return baseRedisSupport.delete(key);
    }
}
