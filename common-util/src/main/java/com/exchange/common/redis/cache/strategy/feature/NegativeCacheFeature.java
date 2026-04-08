package com.exchange.common.redis.cache.strategy.feature;

import com.exchange.common.result.Result;
import com.exchange.common.utils.TtlStrategy;

public interface NegativeCacheFeature {
    Result<Void> set(String key, TtlStrategy ttlStrategy);
    Result<Boolean> clear(String key);

    static NegativeCacheFeature disable() {
        return new NegativeCacheFeature() {
            @Override
            public Result<Void> set(String key, TtlStrategy ttlStrategy) {
                return Result.success();
            }

            @Override
            public Result<Boolean> clear(String key) {
                return Result.success(true);
            }
        };
    }
}
