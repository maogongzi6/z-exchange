package com.exchange.common.redis.cache.ops.impl;

import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.ops.VersionTombstoneOps;
import com.exchange.common.redis.cache.strategy.discriptor.CacheDescriptor;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class VersionTombstoneOpsImpl implements VersionTombstoneOps {
    private final VersionCacheClient versionedCacheRedisClient;
    private final CacheDescriptor<?> cacheDescriptor;

    @Override
    public Result<Boolean> setTombstone(String id, long newVersion, TtlStrategy ttl) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Result<Boolean> result = versionedCacheRedisClient.setTombstone(cacheKey, newVersion, ttl);
        if (!result.success) {
            log.error("cache set failed, key: {}, result: {}", cacheKey, result);
        }
        return result;
    }
}
