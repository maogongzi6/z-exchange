package com.exchange.common.redis.cache.component.support;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.result.Result;

public interface ReadableCache {
    <T> Result<CacheValueInfo<T>> get(String key, Class<T> clazz);
}
