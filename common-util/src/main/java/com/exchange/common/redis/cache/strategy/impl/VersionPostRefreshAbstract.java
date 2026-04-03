package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.strategy.VersionNegativeCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionPostRefreshStrategy;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.extern.slf4j.Slf4j;

// No Tombstone logic for post-refresh strategy
@Slf4j
public abstract class VersionPostRefreshAbstract<T> extends VersionAbstract<T> implements VersionPostRefreshStrategy<T>, VersionNegativeCacheStrategy<T> {
    private final VersionCacheClient versionedCacheRedisClient;

    public VersionPostRefreshAbstract(VersionCacheClient versionedCacheRedisClient) {
        super(versionedCacheRedisClient);
        this.versionedCacheRedisClient = versionedCacheRedisClient;
    }
    @Override
    public Result<Boolean> setAfterDbCommit(String id, T value, long newVersion, TtlStrategy ttl) {
        return set(id, value, newVersion, ttl);
    }

    @Override
    public Result<Boolean> setNegative(String id, long version, TtlStrategy ttl) {
        String cacheKey = getCacheKey(id);
        Result<Boolean> setResult = versionedCacheRedisClient.setNegative(cacheKey, version, ttl);
        if (!setResult.success) {
            // cache error should not block the main flow
            log.error("negative cache set failed, cache_key: {}, version: {}, result: {}", cacheKey, version, setResult);
        } else if (!setResult.value) {
            log.debug("negative cache not set, cache_key: {}, version: {}", cacheKey, version);
        }
        return setResult;
    }

    @Override
    public Result<Boolean> cleanNegative(String id) {
        String cacheKey = getCacheKey(id);
        // TODO clean with CAS to prevent from mis-cleaning cached data
        Boolean deleted = versionedCacheRedisClient.delete(cacheKey);
        return Results.success(deleted);
    }
}
