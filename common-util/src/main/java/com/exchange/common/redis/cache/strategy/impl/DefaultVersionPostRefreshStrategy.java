package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.ops.ReadOps;
import com.exchange.common.redis.cache.ops.SelfRecoverOps;
import com.exchange.common.redis.cache.ops.VersionWriteOps;
import com.exchange.common.redis.cache.ops.VersionNegativeCacheOps;
import com.exchange.common.redis.cache.ops.impl.L1L2ReadOpsImpl;
import com.exchange.common.redis.cache.strategy.VersionPostRefreshStrategy;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// No Tombstone logic for post-refresh strategy
@Slf4j
public class DefaultVersionPostRefreshStrategy<T> implements VersionPostRefreshStrategy<T> {
    private final ReadOps<T> readOps;
    private final VersionWriteOps<T> versionBaseOps;
    private final VersionNegativeCacheOps versionNegativeCacheOps;
    private final SelfRecoverOps selfRecoverOps;

    DefaultVersionPostRefreshStrategy(ReadOps<T> readOps, VersionWriteOps<T> versionBaseOps, VersionNegativeCacheOps versionNegativeCacheOps, SelfRecoverOps selfRecoverOps) {
        this.readOps = readOps;
        this.versionBaseOps = versionBaseOps;
        this.versionNegativeCacheOps = versionNegativeCacheOps;
        this.selfRecoverOps = selfRecoverOps;
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
    public Result<Boolean> set(String id, T value, long newVersion, TtlStrategy ttl) {
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
