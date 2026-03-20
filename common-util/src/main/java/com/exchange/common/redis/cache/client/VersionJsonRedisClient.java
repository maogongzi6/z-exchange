package com.exchange.common.redis.cache.client;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.support.VersionCacheWriteSupport;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
public class VersionJsonRedisClient {
    private final BaseRedisSupport<String> baseRedisSupport;
    private final VersionCacheWriteSupport versionCacheWriteSupport;

    public <T> Result<CacheValueInfo<T>> get(String key, Class<T> clazz) {
        return versionCacheWriteSupport.get(key, clazz);
    }

    public <T> Result<Boolean> setIfAbsentOrNewer(String key, T value, long newVersion, Duration ttl) {
        return versionCacheWriteSupport.setIfAbsentOrNewer(key, value, newVersion, ttl);
    }

    public Result<Boolean> setTombstone(String key, long newVersion, Duration ttl) {
        return versionCacheWriteSupport.setTombstone(key, newVersion, ttl);
    }

    public Boolean delete(String key) {
        return baseRedisSupport.delete(key);
    }
}
