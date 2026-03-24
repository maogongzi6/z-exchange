package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.strategy.impl.VersionPostRefreshStrategy;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@AllArgsConstructor
public abstract class VersionPostRefreshAbstract<T> implements VersionPostRefreshStrategy<T> {
    private final VersionCacheClient versionedCacheRedisClient;

    // TODO maybe use inheritance to reuse methods like get/setIfAbsent
    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        String cacheKey = getCacheKey(id);
        // check cache
        // if id is from our service and used internally,
        // it means it is low risky to be attacked, and querying non-existed txn is also rare
        // then, do not set negative value (NULL) when cache misses.
        // if not, we need to set a negative value
        Result<CacheValueInfo<T>> cacheResult = versionedCacheRedisClient.get(cacheKey, getClazz());
        if (!cacheResult.success) {
            // cache error should not block the main flow
            log.error("cache get failed, cache_key: {}, result: {}", cacheKey, cacheResult);
            if (Result.is(cacheResult, CommonErrorCode.PARSE_CACHE_ERROR)) {
                // delete the abnormal value to fast recover
                log.error("invalid cache value, delete to fast recover, cache_key: {}, result: {}", cacheKey, cacheResult);
                Boolean deleted = versionedCacheRedisClient.delete(cacheKey);
                if (!deleted) {
                    log.error("cache delete failed, cache_key: {}", cacheKey);
                }
            }
        }
        return cacheResult;
    }

    @Override
    public Result<Boolean> setAfterDbCommit(String id, T value, long newVersion, TtlStrategy ttl) {
        String cacheKey = getCacheKey(id);
        Result<Boolean> setResult = versionedCacheRedisClient.setIfAbsentOrNewer(cacheKey, value, newVersion, ttl);
        if (!setResult.success) {
            // cache error should not block the main flow
            log.error("cache set failed, cache_key: {}, value: {}, result: {}", cacheKey, value, setResult);
        } else if (!setResult.value) {
            log.debug("cache not set, cache_key: {}, value: {}", cacheKey, value);
        }
        return setResult;
    }

}
