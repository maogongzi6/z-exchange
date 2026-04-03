package com.exchange.common.redis.cache.ops.impl;

import com.exchange.common.redis.cache.client.SimpleCacheClient;
import com.exchange.common.redis.cache.ops.NegativeCacheOps;
import com.exchange.common.redis.cache.strategy.discriptor.CacheDescriptor;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class NegativeCacheOpsImpl implements NegativeCacheOps {
    private final SimpleCacheClient simpleCacheClient;
    private final CacheDescriptor<?> cacheDescriptor;

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
        return simpleCacheClient.delete(cacheKey);
    }
}
