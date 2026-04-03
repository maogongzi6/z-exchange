package com.exchange.common.redis.cache.ops;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface NegativeCacheOps {
    Result<Void> setNegative(String id, TtlStrategy ttl);
    Result<Boolean> cleanNegative(String id);
}
