package com.exchange.common.redis.cache.ops;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.result.Result;

public interface ReadOps<T> {
    Result<CacheValueInfo<T>> get(String id);
}
