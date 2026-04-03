package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.ops.ReadCacheOps;
import com.exchange.common.redis.cache.ops.VersionNegativeCacheOps;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface VersionPostRefreshStrategy<T> extends ReadCacheOps<T>, VersionNegativeCacheOps {
    Result<Boolean> setAfterDbCommit(String id, T value, long newVersion, TtlStrategy ttl);
}
