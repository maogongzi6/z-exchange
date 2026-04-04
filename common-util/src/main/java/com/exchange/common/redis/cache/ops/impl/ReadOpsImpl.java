package com.exchange.common.redis.cache.ops.impl;

import com.exchange.common.redis.cache.component.support.ReadableCache;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.ops.ReadOps;
import com.exchange.common.redis.cache.strategy.CacheDescriptor;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ReadOpsImpl<T> implements ReadOps<T> {
    private final ReadableCache cacheReader;
    private final CacheDescriptor<T> cacheDescriptor;

    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Result<CacheValueInfo<T>> refCacheResult = cacheReader.get(cacheKey, cacheDescriptor.getClazz());
        if (!refCacheResult.success()) {
            log.error("cache get failed, cache_key: {}, result: {}", cacheKey, refCacheResult);
        }
        return refCacheResult;
    }
}
