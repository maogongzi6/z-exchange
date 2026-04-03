package com.exchange.common.redis.cache.strategy;

public interface BaseStrategy<T> {
    String getCacheKey(String id);
    Class<T> getClazz();
}
