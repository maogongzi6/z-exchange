package com.exchange.common.redis.cache.ops.impl;

import com.exchange.common.redis.cache.component.support.VersionCache;
import com.exchange.common.redis.cache.ops.VersionTombstoneOps;
import com.exchange.common.redis.cache.strategy.CacheDescriptor;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class VersionTombstoneOpsImpl implements VersionTombstoneOps {
    private final VersionCache versionedRedisSupport;
    private final CacheDescriptor<?> cacheDescriptor;

    @Override
    public Result<Boolean> setTombstone(String id, long newVersion, TtlStrategy ttl) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Result<Boolean> result = versionedRedisSupport.setTombstone(cacheKey, newVersion, ttl);
        if (!result.success()) {
            log.error("cache set failed, key: {}, result: {}", cacheKey, result);
        }
        return result;
    }
}
