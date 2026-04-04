package com.exchange.common.redis.cache.ops;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface SimpleWriteOps<T> {
    Result<Void> set(String id, T value, TtlStrategy ttl);
}
