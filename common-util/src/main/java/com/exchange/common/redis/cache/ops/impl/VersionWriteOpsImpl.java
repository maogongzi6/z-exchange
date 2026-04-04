package com.exchange.common.redis.cache.ops.impl;

import com.exchange.common.redis.cache.component.support.VersionCache;
import com.exchange.common.redis.cache.ops.VersionWriteOps;
import com.exchange.common.redis.cache.strategy.CacheDescriptor;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class VersionWriteOpsImpl<T> implements VersionWriteOps<T> {
    private final VersionCache versionedRedisSupport;
    private final CacheDescriptor<T> cacheDescriptor;

    @Override
    public Result<Boolean> set(String id, T value, long newVersion, TtlStrategy ttl) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Result<Boolean> setResult = versionedRedisSupport.setIfAbsentOrNewer(cacheKey, value, newVersion, ttl);
        if (!setResult.success()) {
            // cache error should not block the main flow
            log.error("cache set failed, cache_key: {}, value: {}, result: {}", cacheKey, value, setResult);
        } else if (!setResult.value()) {
            log.debug("cache not set, cache_key: {}, value: {}", cacheKey, value);
        }
        return setResult;
    }
}
