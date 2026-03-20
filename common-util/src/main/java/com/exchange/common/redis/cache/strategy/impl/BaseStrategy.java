package com.exchange.common.redis.cache.strategy.impl;

public interface BaseStrategy<T> {
    String getCacheKey(String id);
    Class<T> getClazz();
}
