package com.exchange.common.redis.cache.ops;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface SimpleBaseOps<T> extends ReadCacheOps<T> {
    Result<Void> set(String id, T value, TtlStrategy ttl);
}
