package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.ops.NegativeCacheOps;
import com.exchange.common.redis.cache.ops.SimpleBaseOps;

public interface StableCacheStrategy<T> extends SimpleBaseOps<T>, NegativeCacheOps {
}
