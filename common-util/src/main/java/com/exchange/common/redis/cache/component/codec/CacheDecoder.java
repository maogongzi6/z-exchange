package com.exchange.common.redis.cache.component.codec;

import com.exchange.common.redis.cache.model.CacheValueInfo;

public interface CacheDecoder {
    <T> CacheValueInfo<T> decode(String value, Class<T> clazz);
}
