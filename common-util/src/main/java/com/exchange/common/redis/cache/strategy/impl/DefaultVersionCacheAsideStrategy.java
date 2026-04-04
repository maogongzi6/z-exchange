package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.ops.*;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultVersionCacheAsideStrategy<T> implements VersionCacheStrategy<T> {
    private final ReadOps<T> readOps;
    private final VersionWriteOps<T> versionBaseOps;
    private final VersionTombstoneOps versionTombstoneOps;
    private final VersionNegativeCacheOps versionNegativeCacheOps;
    private final SelfRecoverOps selfRecoverOps;

    DefaultVersionCacheAsideStrategy(ReadOps<T> readOps, VersionWriteOps<T> versionBaseOps, VersionTombstoneOps versionTombstoneOps, VersionNegativeCacheOps versionNegativeCacheOps, SelfRecoverOps selfRecoverOps) {
        this.readOps = readOps;
        this.versionBaseOps = versionBaseOps;
        this.versionTombstoneOps = versionTombstoneOps;
        this.versionNegativeCacheOps = versionNegativeCacheOps;
        this.selfRecoverOps = selfRecoverOps;
    }

    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        var result = readOps.get(id);
        if (!result.success() && Results.is(result, CommonErrorCode.PARSE_CACHE_ERROR)) {
            // ignore recover error
            selfRecoverOps.recover(id);
        }
        return result;
    }

    @Override
    public Result<Boolean> afterQueryHit(String id, T value, long newVersion, TtlStrategy ttl) {
        return versionBaseOps.set(id, value, newVersion, ttl);
    }

    @Override
    public Result<Boolean> afterUpdate(String id, long newVersion, TtlStrategy ttl) {
        return versionTombstoneOps.setTombstone(id, newVersion, ttl);
    }

    @Override
    public Result<Boolean> afterQueryMiss(String id, long version, TtlStrategy ttl) {
        return versionNegativeCacheOps.setNegative(id, version, ttl);
    }

    @Override
    public Result<Boolean> afterInsert(String id) {
        return versionNegativeCacheOps.cleanNegative(id);
    }
}
