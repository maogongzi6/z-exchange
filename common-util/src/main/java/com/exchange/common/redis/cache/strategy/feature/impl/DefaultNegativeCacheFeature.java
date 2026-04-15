package com.exchange.common.redis.cache.strategy.feature.impl;

import com.exchange.common.redis.cache.component.support.NegativeWriter;
import com.exchange.common.redis.cache.component.support.RawCacheDeleter;
import com.exchange.common.redis.cache.strategy.feature.NegativeCacheFeature;
import com.exchange.common.result.Result;
import com.exchange.common.utils.TtlStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultNegativeCacheFeature implements NegativeCacheFeature {
    private final NegativeWriter negativeWriter;
    private final RawCacheDeleter rawCacheDeleter;

    @Override
    public Result<Void> set(String key, TtlStrategy ttlStrategy) {
        negativeWriter.setNegative(key, ttlStrategy);
        return Result.success();
    }

    @Override
    public Result<Boolean> clear(String key) {
        return Result.success(rawCacheDeleter.delete(key));
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
