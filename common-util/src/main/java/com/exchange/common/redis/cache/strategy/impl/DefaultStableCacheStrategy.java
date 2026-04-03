package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.client.SimpleCacheClient;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.redis.cache.strategy.discriptor.CacheDescriptor;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultStableCacheStrategy<T> implements StableCacheStrategy<T> {
    private final SimpleCacheClient simpleCacheClient;
    private final CacheDescriptor<T> cacheDescriptor;

    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Result<CacheValueInfo<T>> refCacheResult = simpleCacheClient.get(cacheKey, cacheDescriptor.getClazz());
        if (!refCacheResult.success) {
            // TODO customized self-recover
            log.error("cache get failed, cache_key: {}, result: {}", cacheKey, refCacheResult);
            if (Result.is(refCacheResult, CommonErrorCode.PARSE_CACHE_ERROR)) {
                // delete the abnormal value to fast recover
                log.error("invalid cache value, delete to fast recover, cache_key: {}, result: {}", cacheKey, refCacheResult);
                Boolean deleted = simpleCacheClient.delete(cacheKey);
                if (!deleted) {
                    log.error("cache delete failed, cache_key: {}", cacheKey);
                }
            }
        }
        return refCacheResult;
    }

    @Override
    public Result<Void> set(String id, T value, TtlStrategy ttl) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Result<Void> setResult = simpleCacheClient.set(cacheKey, value, ttl);
        if (!setResult.success) {
            log.error("cache set failed, cache_key: {}, value: {}, result: {}", cacheKey, value, setResult);
        }
        return setResult;
    }

    // if id is from our service and used internally,
    // it means it is low risky to be attacked, and querying non-existed txn is also rare
    // then, do not set negative value (NULL) when cache misses.
    // if not, we need to set a negative value
    @Override
    public Result<Void> setNegative(String id, TtlStrategy ttl) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Result<Void> setNegativeResult = simpleCacheClient.setNegative(cacheKey, ttl);
        if (!setNegativeResult.success) {
            log.error("negative cache set failed, cache_key: {}, result: {}", cacheKey, setNegativeResult);
        }
        return setNegativeResult;
    }

    @Override
    public Result<Boolean> cleanNegative(String id) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Boolean deleted = simpleCacheClient.delete(cacheKey);
        return Results.success(deleted);
    }
}
