package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.ops.VersionBaseOps;
import com.exchange.common.redis.cache.ops.VersionNegativeCacheOps;
import com.exchange.common.redis.cache.strategy.VersionPostRefreshStrategy;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// No Tombstone logic for post-refresh strategy
@Slf4j
@RequiredArgsConstructor
public class DefaultVersionPostRefreshStrategy<T> implements VersionPostRefreshStrategy<T> {
    private final VersionBaseOps<T> versionBaseOps;
    private final VersionNegativeCacheOps versionNegativeCacheOps;

    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        return versionBaseOps.get(id);
    }

    @Override
    public Result<Boolean> setAfterDbCommit(String id, T value, long newVersion, TtlStrategy ttl) {
        return versionBaseOps.set(id, value, newVersion, ttl);
    }

    @Override
    public Result<Boolean> setNegative(String id, long version, TtlStrategy ttl) {
        return versionNegativeCacheOps.setNegative(id, version, ttl);
    }

    @Override
    public Result<Boolean> cleanNegative(String id) {
        return versionNegativeCacheOps.cleanNegative(id);
    }
}
