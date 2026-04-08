package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.result.Result;

public interface VersionCacheStrategy<T> {
    Result<CacheValueInfo<T>> get(String id);
    Result<Boolean> afterDbHit(String id, T value, long newVersion);
    Result<Boolean> afterDbMiss(String id);
    Result<Boolean> afterUpdate(String id, T value, long newVersion);
    Result<Boolean> afterInsert(String id, T value, long newVersion);
}
