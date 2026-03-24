package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface VersionNegativeCacheStrategy<T> extends BaseStrategy<T> {
    Result<Void> setNegative(String id, long version, TtlStrategy ttl);
}
