package com.exchange.common.redis.cache.component.codec;

import com.exchange.common.redis.cache.model.CacheReadResult;

public interface CacheDecoder {
    <T> CacheReadResult<T> decode(String value, Class<T> clazz);
}
