package com.exchange.common.redis.cache.ops.impl;

import com.exchange.common.redis.cache.client.SimpleCacheClient;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.ops.ReadCacheOps;
import com.exchange.common.redis.cache.ops.SimpleBaseOps;
import com.exchange.common.redis.cache.strategy.discriptor.CacheDescriptor;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SimpleBaseOpsImpl<T> implements SimpleBaseOps<T> {
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
                Result<Boolean> deleteResult = simpleCacheClient.delete(cacheKey);
                if (!deleteResult.success || !deleteResult.value) {
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
}
