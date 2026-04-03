package com.exchange.common.redis.cache.ops.impl;

import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.ops.VersionNegativeCacheOps;
import com.exchange.common.redis.cache.strategy.discriptor.CacheDescriptor;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class VersionNegativeCacheOpsImpl implements VersionNegativeCacheOps {
    private final VersionCacheClient versionedCacheRedisClient;
    private final CacheDescriptor<?> cacheDescriptor;

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
        return versionedCacheRedisClient.delete(cacheKey);
    }
}
