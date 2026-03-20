package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.client.SimpleCacheClient;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.strategy.impl.StableCacheStrategy;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public abstract class StableCacheAbstract<T> implements StableCacheStrategy<T> {
    private final SimpleCacheClient simpleCacheClient;

    // TODO maybe use BaseCacheAbstract
    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        String cacheKey = getCacheKey(id);
        Result<CacheValueInfo<T>> refCacheResult = simpleCacheClient.get(cacheKey, getClazz());
        if (!refCacheResult.success) {
            // TODO customized self-recover
            // cache error should not block the main flow
            log.error("cache get failed, cache_key: {}, result: {}", id, refCacheResult);
            if (Result.is(refCacheResult, CommonErrorCode.PARSE_CACHE_ERROR)) {
                // delete the abnormal value to fast recover
                log.error("invalid cache value, delete to fast recover, cache_key: {}, result: {}", cacheKey, refCacheResult);
                simpleCacheClient.delete(cacheKey);
            }
        }
        return refCacheResult;
    }

    @Override
    public Result<Void> set(String id, T value, TtlStrategy ttl) {
        String cacheKey = getCacheKey(id);
        Result<Void> setResult = simpleCacheClient.set(cacheKey, value, ttl);
        if (!setResult.success) {
            // cache error should not block the main flow
            log.error("cache set failed, cache_key: {}, value: {}, result: {}", cacheKey, value, setResult);
        }
        return setResult;
    }
}
