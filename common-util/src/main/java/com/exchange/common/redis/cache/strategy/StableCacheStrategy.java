package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.ops.NegativeCacheOps;
import com.exchange.common.redis.cache.ops.ReadOps;
import com.exchange.common.redis.cache.ops.SimpleWriteOps;

public interface StableCacheStrategy<T> extends ReadOps<T>, SimpleWriteOps<T>, NegativeCacheOps {
}
