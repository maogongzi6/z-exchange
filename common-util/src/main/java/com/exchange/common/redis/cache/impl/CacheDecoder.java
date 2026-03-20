package com.exchange.common.redis.cache.impl;

import com.exchange.common.exception.CacheParseException;
import com.exchange.common.redis.cache.helper.CacheValueInfo;

public interface CacheDecoder {
    <T> CacheValueInfo<T> decode(String value, Class<T> clazz) throws CacheParseException;
}
