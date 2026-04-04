package com.exchange.common.redis.cache.ops.impl;

import com.exchange.common.redis.cache.component.support.VersionedRedisSupport;
import com.exchange.common.redis.cache.ops.VersionWriteOps;
import com.exchange.common.redis.cache.strategy.CacheDescriptor;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class VersionWriteOpsImpl<T> implements VersionWriteOps<T> {
    private final VersionedRedisSupport versionedRedisSupport;
    private final CacheDescriptor<T> cacheDescriptor;

//    @Override
//    public Result<CacheValueInfo<T>> get(String id) {
//        String cacheKey = cacheDescriptor.buildCacheKey(id);
//        Result<CacheValueInfo<T>> cacheResult = versionedRedisSupport.get(cacheKey, cacheDescriptor.getClazz());
//        if (!cacheResult.success) {
//            // cache error should not block the main flow
//            log.error("cache get failed, cache_key: {}, result: {}", cacheKey, cacheResult);
//            if (Result.is(cacheResult, CommonErrorCode.PARSE_CACHE_ERROR)) {
//                // delete the abnormal value to fast recover
//                log.error("invalid cache value, delete to fast recover, cache_key: {}, result: {}", cacheKey, cacheResult);
//                Result<Boolean> deleteResult = versionedRedisSupport.delete(cacheKey);
//                if (!deleteResult.success || !deleteResult.value) {
//                    log.error("cache delete failed, cache_key: {}", cacheKey);
//                }
//            }
//        }
//        return cacheResult;
//    }

    @Override
    public Result<Boolean> set(String id, T value, long newVersion, TtlStrategy ttl) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Result<Boolean> setResult = versionedRedisSupport.setIfAbsentOrNewer(cacheKey, value, newVersion, ttl);
        if (!setResult.success()) {
            // cache error should not block the main flow
            log.error("cache set failed, cache_key: {}, value: {}, result: {}", cacheKey, value, setResult);
        } else if (!setResult.value()) {
            log.debug("cache not set, cache_key: {}, value: {}", cacheKey, value);
        }
        return setResult;
    }
}
