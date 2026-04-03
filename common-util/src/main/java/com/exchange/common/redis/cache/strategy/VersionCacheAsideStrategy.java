package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.strategy.ops.ReadCacheOps;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface VersionCacheAsideStrategy<T> extends ReadCacheOps<T> {
    Result<Boolean> setCacheAside(String id, T value, long newVersion, TtlStrategy ttl);
    Result<Boolean> setTombstoneAfterWrite(String id, long newVersion, TtlStrategy ttl);
}
