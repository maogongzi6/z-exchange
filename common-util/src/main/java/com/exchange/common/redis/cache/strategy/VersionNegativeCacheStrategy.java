package com.exchange.common.redis.cache.strategy;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface VersionNegativeCacheStrategy<T> extends BaseStrategy<T> {
    Result<Boolean> setNegative(String id, long version, TtlStrategy ttl);
    Result<Boolean> cleanNegative(String id);
}
