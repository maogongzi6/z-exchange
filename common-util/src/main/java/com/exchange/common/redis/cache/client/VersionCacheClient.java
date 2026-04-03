package com.exchange.common.redis.cache.client;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.support.VersionedRedisSupport;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class VersionCacheClient implements CacheReader {
    private final BaseRedisSupport<String> baseRedisSupport;
    private final VersionedRedisSupport versionRedisSupport;

    public <T> Result<CacheValueInfo<T>> get(String key, Class<T> clazz) {
        return versionRedisSupport.get(key, clazz);
    }

    public <T> Result<Boolean> setIfAbsentOrNewer(String key, T value, long newVersion, TtlStrategy ttl) {
        return versionRedisSupport.setIfAbsentOrNewer(key, value, newVersion, ttl.afterJitter());
    }

    public Result<Boolean> setTombstone(String key, long newVersion, TtlStrategy ttl) {
        return versionRedisSupport.setTombstone(key, newVersion, ttl.afterJitter());
    }

    public Result<Boolean> setNegative(String key, long newVersion, TtlStrategy ttl) {
        return versionRedisSupport.setNegative(key, newVersion, ttl.afterJitter());
    }

    public Boolean delete(String key) {
        return baseRedisSupport.delete(key);
    }
}
