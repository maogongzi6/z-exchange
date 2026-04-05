package com.exchange.common.redis.cache.strategy.feature;

import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;

public interface NegativeCacheFeature {
    Result<Void> set(String key, TtlStrategy ttlStrategy);
    Result<Boolean> clear(String key);

    static NegativeCacheFeature disable() {
        return new NegativeCacheFeature() {
            @Override
            public Result<Void> set(String key, TtlStrategy ttlStrategy) {
                return Results.success();
            }

            @Override
            public Result<Boolean> clear(String key) {
                return Results.success(true);
            }
        };
    }
}
