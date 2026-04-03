package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.strategy.VersionCacheAsideStrategy;
import com.exchange.common.redis.cache.strategy.VersionStrategy;
import com.exchange.common.redis.cache.strategy.discriptor.CacheDescriptor;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultVersionCacheAsideStrategy<T> implements VersionCacheAsideStrategy<T> {
    private final VersionCacheClient versionedCacheRedisClient;
    private final VersionStrategy<T> versionStrategy;
    private final CacheDescriptor<T> cacheDescriptor;

    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        return versionStrategy.get(id);
    }

    @Override
    public Result<Boolean> setCacheAside(String id, T value, long newVersion, TtlStrategy ttl) {
        return versionStrategy.set(id, value, newVersion, ttl);
    }

    @Override
    public Result<Boolean> setTombstoneAfterWrite(String id, long newVersion, TtlStrategy ttl) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Result<Boolean> result = versionedCacheRedisClient.setTombstone(cacheKey, newVersion, ttl);
        if (!result.success) {
            log.error("cache set failed, key: {}, result: {}", cacheKey, result);
        }
        return result;
    }
}
