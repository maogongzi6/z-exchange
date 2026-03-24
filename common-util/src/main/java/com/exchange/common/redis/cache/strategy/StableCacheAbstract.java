package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.client.SimpleCacheClient;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.strategy.impl.NegativeCacheStrategy;
import com.exchange.common.redis.cache.strategy.impl.StableCacheStrategy;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public abstract class StableCacheAbstract<T> implements StableCacheStrategy<T>, NegativeCacheStrategy<T> {
    private final SimpleCacheClient simpleCacheClient;

    // TODO maybe use BaseCacheAbstract
    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        String cacheKey = getCacheKey(id);
        Result<CacheValueInfo<T>> refCacheResult = simpleCacheClient.get(cacheKey, getClazz());
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
        String cacheKey = getCacheKey(id);
        Result<Void> setResult = simpleCacheClient.set(cacheKey, value, ttl);
        if (!setResult.success) {
            log.error("cache set failed, cache_key: {}, value: {}, result: {}", cacheKey, value, setResult);
        }
        return setResult;
    }

    @Override
    public Result<Void> setNegative(String id, TtlStrategy ttl) {
        String cacheKey = getCacheKey(id);
        Result<Void> setNegativeResult = simpleCacheClient.setNegative(cacheKey, ttl);
        if (!setNegativeResult.success) {
            log.error("negative cache set failed, cache_key: {}, result: {}", cacheKey, setNegativeResult);
        }
        return setNegativeResult;
    }

    @Override
    public Result<Void> cleanNegative(String id) {
        String cacheKey = getCacheKey(id);
        Boolean deleted = simpleCacheClient.delete(cacheKey);
        return Results.success();
    }
}
