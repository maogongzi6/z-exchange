package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.ops.NegativeCacheOps;
import com.exchange.common.redis.cache.ops.ReadOps;
import com.exchange.common.redis.cache.ops.SelfRecoverOps;
import com.exchange.common.redis.cache.ops.SimpleWriteOps;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultStableCacheStrategy<T> implements StableCacheStrategy<T> {
    private final ReadOps<T> readOps;
    private final SimpleWriteOps<T> simpleBaseOps;
    private final NegativeCacheOps negativeCacheOps;
    private final SelfRecoverOps selfRecoverOps;

    DefaultStableCacheStrategy(ReadOps<T> readOps, SimpleWriteOps<T> simpleBaseOps, NegativeCacheOps negativeCacheOps, SelfRecoverOps selfRecoverOps) {
        this.readOps = readOps;
        this.simpleBaseOps = simpleBaseOps;
        this.negativeCacheOps = negativeCacheOps;
        this.selfRecoverOps = selfRecoverOps;
    }

    @Override
    public Result<CacheValueInfo<T>> get(String id) {
        var result = readOps.get(id);
        if (!result.success() && Results.is(result, CommonErrorCode.PARSE_CACHE_ERROR)) {
            // ignore recover error
            selfRecoverOps.recover(id);
        }
        return result;
    }

    @Override
    public Result<Void> set(String id, T value, TtlStrategy ttl) {
        return simpleBaseOps.set(id, value, ttl);
    }

    // if id is from our service and used internally,
    // it means it is low risky to be attacked, and querying non-existed txn is also rare
    // then, do not set negative value (NULL) when cache misses.
    // if not, we need to set a negative value
    @Override
    public Result<Void> setNegative(String id, TtlStrategy ttl) {
        return negativeCacheOps.setNegative(id, ttl);
    }

    @Override
    public Result<Boolean> cleanNegative(String id) {
        return negativeCacheOps.cleanNegative(id);
    }
}
