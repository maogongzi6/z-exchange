package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface StableCacheStrategy<T> extends BaseReadStrategy<T> {
    Result<Void> set(String key, T value, TtlStrategy ttl);
}
