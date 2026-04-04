package com.exchange.common.redis.cache.ops.impl;

import com.exchange.common.redis.cache.component.support.VersionCache;
import com.exchange.common.redis.cache.ops.VersionNegativeCacheOps;
import com.exchange.common.redis.cache.strategy.CacheDescriptor;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class VersionNegativeCacheOpsImpl implements VersionNegativeCacheOps {
    private final VersionCache versionedRedisSupport;
    private final CacheDescriptor<?> cacheDescriptor;

    @Override
    public Result<Boolean> setNegative(String id, TtlStrategy ttl) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Result<Boolean> setResult = versionedRedisSupport.setNegative(cacheKey, 0L, ttl);
        if (!setResult.success()) {
            // cache error should not block the main flow
            log.error("negative cache set failed, cache_key: {}, result: {}", cacheKey, setResult);
        } else if (!setResult.value()) {
            log.debug("negative cache not set, cache_key: {}", cacheKey);
        }
        return setResult;
    }

    @Override
    public Result<Boolean> cleanNegative(String id) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        // TODO clean with CAS to prevent from mis-cleaning cached data
        return versionedRedisSupport.delete(cacheKey);
    }
}
