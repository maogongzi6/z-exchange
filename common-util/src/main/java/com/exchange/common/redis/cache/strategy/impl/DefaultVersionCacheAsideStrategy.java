package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.model.StrategyOption;
import com.exchange.common.redis.cache.ops.*;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultVersionCacheAsideStrategy<T> implements VersionCacheStrategy<T> {
    private final ReadOps<T> readOps;
    private final VersionWriteOps<T> versionWriteOps;
    private final VersionTombstoneOps versionTombstoneOps;
    private final VersionNegativeCacheOps versionNegativeCacheOps;
    private final SelfRecoverOps selfRecoverOps;
    private final StrategyOption option;

    // package private constructor. expose factory method for external user
    DefaultVersionCacheAsideStrategy(ReadOps<T> readOps, VersionWriteOps<T> versionWriteOps, VersionTombstoneOps versionTombstoneOps, VersionNegativeCacheOps versionNegativeCacheOps, SelfRecoverOps selfRecoverOps, StrategyOption option) {
        this.readOps = readOps;
        this.versionWriteOps = versionWriteOps;
        this.versionTombstoneOps = versionTombstoneOps;
        this.versionNegativeCacheOps = versionNegativeCacheOps;
        this.selfRecoverOps = selfRecoverOps;
        this.option = option;
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
    public Result<Boolean> afterDbHit(String id, T value, long newVersion) {
        return versionWriteOps.set(id, value, newVersion, option.ttlOption().entityTtl());
    }

    @Override
    public Result<Boolean> afterDbMiss(String id) {
        if (!option.requireNegativeCache()) {
            return Results.success();
        }
        return versionNegativeCacheOps.setNegative(id, option.ttlOption().negativeTtl());
    }

    @Override
    public Result<Boolean> afterUpdate(String id, T value, long newVersion) {
        if (!option.requireTombstone()) {
            return Results.success();
        }
        return versionTombstoneOps.setTombstone(id, newVersion, option.ttlOption().tombstoneTtl());
    }

    @Override
    public Result<Boolean> afterInsert(String id, T value, long newVersion) {
        if (!option.requireNegativeCache()) {
            return Results.success();
        }
        return versionNegativeCacheOps.cleanNegative(id);
    }
}
