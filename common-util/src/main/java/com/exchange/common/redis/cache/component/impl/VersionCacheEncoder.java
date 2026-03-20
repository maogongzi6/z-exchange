package com.exchange.common.redis.cache.component.impl;

import com.exchange.common.exception.CacheParseException;
import com.exchange.common.redis.cache.constant.CacheType;

public interface VersionCacheEncoder {
    <T> String encode(T value, CacheType cacheType, long version) throws CacheParseException;
}
