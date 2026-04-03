package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.ops.VersionBaseOps;
import com.exchange.common.redis.cache.ops.VersionTombstoneOps;
import com.exchange.common.redis.cache.strategy.VersionCacheAsideStrategy;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultVersionCacheAsideStrategy<T> implements VersionCacheAsideStrategy<T> {
    private final VersionBaseOps<T> versionBaseOps;
    private final VersionTombstoneOps versionTombstoneOps;

    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        return versionBaseOps.get(id);
    }

    @Override
    public Result<Boolean> setCacheAside(String id, T value, long newVersion, TtlStrategy ttl) {
        return versionBaseOps.set(id, value, newVersion, ttl);
    }

    @Override
    public Result<Boolean> setTombstoneAfterWrite(String id, long newVersion, TtlStrategy ttl) {
        return versionTombstoneOps.setTombstone(id, newVersion, ttl);
    }
}
