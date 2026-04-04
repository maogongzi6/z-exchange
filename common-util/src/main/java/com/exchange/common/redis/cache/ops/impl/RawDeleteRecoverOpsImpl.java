package com.exchange.common.redis.cache.ops.impl;

import com.exchange.common.redis.cache.component.support.RawDeletableCache;
import com.exchange.common.redis.cache.ops.SelfRecoverOps;
import com.exchange.common.redis.cache.strategy.CacheDescriptor;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RawDeleteRecoverOpsImpl implements SelfRecoverOps {
    private final RawDeletableCache cache;
    private final CacheDescriptor<?> cacheDescriptor;

    @Override
    public Result<Boolean> recover(String id) {
        String cacheKey = cacheDescriptor.buildCacheKey(id);
        Result<Boolean> deleteResult = cache.delete(cacheKey);
        if (!deleteResult.success() || !deleteResult.value()) {
            log.error("cache delete failed, cache_key: {}", cacheKey);
        }
        return deleteResult;
    }
}
