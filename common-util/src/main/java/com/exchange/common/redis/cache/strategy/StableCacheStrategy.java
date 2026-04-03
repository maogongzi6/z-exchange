package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.strategy.ops.NegativeCacheOps;
import com.exchange.common.redis.cache.strategy.ops.WriteCacheOps;

public interface StableCacheStrategy<T> extends WriteCacheOps<T>, NegativeCacheOps<T> {
}
