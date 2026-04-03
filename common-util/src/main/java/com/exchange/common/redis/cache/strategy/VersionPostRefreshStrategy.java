package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.strategy.ops.ReadCacheOps;
import com.exchange.common.redis.cache.strategy.ops.VersionNegativeCacheOps;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface VersionPostRefreshStrategy<T> extends ReadCacheOps<T>, VersionNegativeCacheOps<T> {
    Result<Boolean> setAfterDbCommit(String id, T value, long newVersion, TtlStrategy ttl);
}
