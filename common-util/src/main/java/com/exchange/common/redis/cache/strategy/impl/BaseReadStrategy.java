package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.result.Result;

public interface BaseReadStrategy<T> extends BaseStrategy<T> {
    Result<CacheValueInfo<T>> get(String key);
}
