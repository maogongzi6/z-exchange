package com.exchange.common.redis.cache.strategy.ops;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

// refresh cache after the db transaction commit
public interface VersionWriteCacheOps<T> extends ReadCacheOps<T> {
    Result<Boolean> set(String id, T value, long newVersion, TtlStrategy ttl);
}
