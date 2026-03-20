package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface VersionedCacheAsideStrategy<T> extends BaseReadStrategy<T> {
    Result<Boolean> setCacheAside(String key, T value, long newVersion, TtlStrategy ttl);
    Result<Boolean> setTombstoneAfterWrite(String key, long newVersion, TtlStrategy ttl);
}
