package com.exchange.common.redis;

public interface BaseCacheReadSupport<T> {
    T get(String key);
}
