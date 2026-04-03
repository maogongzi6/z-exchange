package com.exchange.common.redis.cache.strategy;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface NegativeCacheStrategy<T> extends BaseStrategy<T> {
    Result<Void> setNegative(String id, TtlStrategy ttl);
    Result<Boolean> cleanNegative(String id);
}
