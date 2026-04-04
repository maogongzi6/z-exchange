package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.ops.ReadOps;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;

public interface VersionCacheStrategy<T> extends ReadOps<T> {
    Result<Boolean> afterQueryHit(String id, T value, long newVersion, TtlStrategy ttl);
    Result<Boolean> afterQueryMiss(String id, long newVersion, TtlStrategy ttl);
    Result<Boolean> afterUpdate(String id, long newVersion, TtlStrategy ttl);
    Result<Boolean> afterInsert(String id);
}
