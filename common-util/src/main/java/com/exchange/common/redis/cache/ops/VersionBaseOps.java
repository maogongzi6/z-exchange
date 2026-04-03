package com.exchange.common.redis.cache.ops;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

// refresh cache after the db transaction commit
public interface VersionBaseOps<T> extends ReadCacheOps<T> {
    Result<Boolean> set(String id, T value, long newVersion, TtlStrategy ttl);
}
