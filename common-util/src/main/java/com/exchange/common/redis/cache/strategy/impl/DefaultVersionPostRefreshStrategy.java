package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.strategy.VersionPostRefreshStrategy;
import com.exchange.common.redis.cache.strategy.VersionStrategy;
import com.exchange.common.redis.cache.strategy.discriptor.CacheDescriptor;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// No Tombstone logic for post-refresh strategy
@Slf4j
@RequiredArgsConstructor
public class DefaultVersionPostRefreshStrategy<T> implements VersionPostRefreshStrategy<T> {
    private final VersionCacheClient versionedCacheRedisClient;
    private final VersionStrategy<T> versionStrategy;
    private final CacheDescriptor<T> cacheDescriptor;

    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        return versionStrategy.get(id);
    }

    @Override
    public Result<Boolean> setAfterDbCommit(String id, T value, long newVersion, TtlStrategy ttl) {
        return versionStrategy.set(id, value, newVersion, ttl);
    }

    @Override
    public Result<Boolean> setNegative(String id, long version, TtlStrategy ttl) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
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
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        // TODO clean with CAS to prevent from mis-cleaning cached data
        Boolean deleted = versionedCacheRedisClient.delete(cacheKey);
        return Results.success(deleted);
    }
}
