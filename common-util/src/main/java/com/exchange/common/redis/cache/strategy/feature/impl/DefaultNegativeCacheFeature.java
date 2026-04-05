package com.exchange.common.redis.cache.strategy.feature.impl;

import com.exchange.common.redis.cache.component.support.NegativeWriter;
import com.exchange.common.redis.cache.component.support.RawCacheDeleter;
import com.exchange.common.redis.cache.strategy.feature.NegativeCacheFeature;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultNegativeCacheFeature implements NegativeCacheFeature {
    private final NegativeWriter negativeWriter;
    private final RawCacheDeleter rawCacheDeleter;

    @Override
    public Result<Void> set(String key, TtlStrategy ttlStrategy) {
        Result<Void> setNegativeResult = negativeWriter.setNegative(key, ttlStrategy);
        if (!setNegativeResult.success()) {
            log.error("negative cache set failed, key: {}, result: {}", key, setNegativeResult);
        }
        return setNegativeResult;
    }

    @Override
    public Result<Boolean> clear(String key) {
        Result<Boolean> result = rawCacheDeleter.delete(key);
        if (!result.success()) {
            log.error("raw cache delete failed, key: {}, result: {}", key, result);
        }
        return result;
    }
}
