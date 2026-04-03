package com.exchange.common.redis.cache.component.codec;

import com.exchange.common.exception.CacheParseException;
import com.exchange.common.redis.cache.constant.CacheType;

public interface CacheEncoder {
    <T> String encode(T value, CacheType cacheType) throws CacheParseException;
}
