package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.model.StrategyOption;
import com.exchange.common.redis.cache.ops.ReadOps;
import com.exchange.common.redis.cache.ops.SelfRecoverOps;
import com.exchange.common.redis.cache.ops.VersionWriteOps;
import com.exchange.common.redis.cache.ops.VersionNegativeCacheOps;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.extern.slf4j.Slf4j;

// No Tombstone logic for post-refresh strategy
@Slf4j
public class DefaultVersionPostRefreshStrategy<T> implements VersionCacheStrategy<T> {
    private final ReadOps<T> readOps;
    private final VersionWriteOps<T> versionBaseOps;
    private final VersionNegativeCacheOps versionNegativeCacheOps;
    private final SelfRecoverOps selfRecoverOps;
    private final StrategyOption option;

    // package private constructor. expose factory method for external user
    DefaultVersionPostRefreshStrategy(ReadOps<T> readOps, VersionWriteOps<T> versionBaseOps, VersionNegativeCacheOps versionNegativeCacheOps, SelfRecoverOps selfRecoverOps, StrategyOption option) {
        this.readOps = readOps;
        this.versionBaseOps = versionBaseOps;
        this.versionNegativeCacheOps = versionNegativeCacheOps;
        this.selfRecoverOps = selfRecoverOps;
        this.option = option;
    }

    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        Result<CacheValueInfo<T>> result = readOps.get(id);
        if (!result.success() && Results.is(result, CommonErrorCode.PARSE_CACHE_ERROR)) {
            // ignore recover error
            selfRecoverOps.recover(id);
        }
        return result;
    }

    @Override
    public Result<Boolean> afterDbHit(String id, T value, long newVersion) {
        return versionBaseOps.set(id, value, newVersion, option.ttlOption().entityTtl());
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
        return afterDbHit(id, value, newVersion);
    }

    @Override
    public Result<Boolean> afterInsert(String id, T value, long newVersion) {
        return afterDbHit(id, value, newVersion);
    }
}
