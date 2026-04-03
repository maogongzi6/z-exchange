package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.strategy.VersionCacheAsideStrategy;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class VersionCacheAsideAbstract<T> extends VersionAbstract<T> implements VersionCacheAsideStrategy<T> {
    private final VersionCacheClient versionedCacheRedisClient;

    public VersionCacheAsideAbstract(VersionCacheClient versionedCacheRedisClient) {
        super(versionedCacheRedisClient);
        this.versionedCacheRedisClient = versionedCacheRedisClient;
    }

    @Override
    public Result<Boolean> setCacheAside(String id, T value, long newVersion, TtlStrategy ttl) {
        return set(id, value, newVersion, ttl);
    }

    @Override
    public Result<Boolean> setTombstoneAfterWrite(String id, long newVersion, TtlStrategy ttl) {
        String cacheKey = getCacheKey(id);
        Result<Boolean> result = versionedCacheRedisClient.setTombstone(cacheKey, newVersion, ttl);
        if (!result.success) {
            log.error("cache set failed, key: {}, result: {}", cacheKey, result);
        }
        return result;
    }
}
