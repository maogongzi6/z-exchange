package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.strategy.ops.ReadCacheOps;
import com.exchange.common.redis.cache.strategy.ops.VersionWriteCacheOps;

public interface VersionStrategy<T> extends ReadCacheOps<T>, VersionWriteCacheOps<T> {
}
